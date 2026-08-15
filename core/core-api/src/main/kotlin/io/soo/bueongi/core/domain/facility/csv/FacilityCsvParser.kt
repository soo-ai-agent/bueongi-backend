package io.soo.bueongi.core.domain.facility.csv

import io.soo.bueongi.core.enums.FacilityCoordSystem

import io.soo.bueongi.core.domain.facility.coord.Epsg5174
import io.soo.bueongi.core.domain.facility.coord.Epsg5179
import io.soo.bueongi.core.domain.facility.coord.Wgs84Point
import io.soo.bueongi.storage.db.core.facility.CCTV_SECURITY_PURPOSES
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

@Component
class FacilityCsvParser {

    fun parse(bytes: ByteArray, spec: FacilityCsvSpec): FacilityCsvParseResult {
        val records: List<List<String>> = tokenizeCsv(decode(bytes))
        val headerPos: Int = findHeaderRow(records)
        if (headerPos < 0) {
            return FacilityCsvParseResult(spec.coordSystem, received = 0, points = emptyList())
        }

        val columns: CsvColumns = locateColumns(buildHeaderIndex(records[headerPos]), spec)
        val dataRows: List<List<String>> = collectDataRows(records, headerPos)

        val points: ArrayList<FacilityPoint> = ArrayList<FacilityPoint>()
        for (row in dataRows) {
            val point: FacilityPoint = toFacilityPoint(row, columns, spec) ?: continue
            points.add(point)
        }
        return FacilityCsvParseResult(columns.coordSystem, received = dataRows.size, points = points)
    }

    /** 첫 비어 있지 않은 행이 헤더다. 없으면 -1. */
    private fun findHeaderRow(records: List<List<String>>): Int {
        for (index in records.indices) {
            if (hasContent(records[index])) {
                return index
            }
        }
        return -1
    }

    private fun collectDataRows(records: List<List<String>>, headerPos: Int): List<List<String>> {
        val rows: ArrayList<List<String>> = ArrayList<List<String>>()
        for (index in headerPos + 1 until records.size) {
            if (hasContent(records[index])) {
                rows.add(records[index])
            }
        }
        return rows
    }

    private fun hasContent(row: List<String>): Boolean {
        for (cell in row) {
            if (cell.isNotBlank()) {
                return true
            }
        }
        return false
    }

    /**
     * 헤더 이름으로 컬럼 위치를 한 번만 찾아 둔다.
     * 위경도 컬럼이 있으면 원천 좌표계와 무관하게 WGS84 로 읽는다(변환 불필요).
     */
    private fun locateColumns(headerIndex: Map<String, Int>, spec: FacilityCsvSpec): CsvColumns {
        val latIdx: Int? = firstIndexOf(headerIndex, spec.latHeaders)
        val lngIdx: Int? = firstIndexOf(headerIndex, spec.lngHeaders)
        val useWgs84: Boolean = latIdx != null && lngIdx != null
        val coordSystem: FacilityCoordSystem = if (useWgs84) {
            FacilityCoordSystem.WGS84
        } else {
            spec.coordSystem
        }
        return CsvColumns(
            useWgs84 = useWgs84,
            coordSystem = coordSystem,
            lat = latIdx,
            lng = lngIdx,
            x = firstIndexOf(headerIndex, spec.xHeaders),
            y = firstIndexOf(headerIndex, spec.yHeaders),
            name = firstIndexOf(headerIndex, spec.nameHeaders),
            address = firstIndexOf(headerIndex, spec.addressHeaders),
            purpose = firstIndexOf(headerIndex, spec.purposeHeaders),
            cameraCount = firstIndexOf(headerIndex, spec.cameraCountHeaders),
            phone = firstIndexOf(headerIndex, spec.phoneHeaders),
        )
    }

    /** 좌표가 없거나 방범 목적이 아닌 행은 버린다(null). */
    private fun toFacilityPoint(row: List<String>, columns: CsvColumns, spec: FacilityCsvSpec): FacilityPoint? {
        val geo: GeoPoint = readGeoPoint(row, columns, spec) ?: return null
        val purpose: String? = row.field(columns.purpose)
        if (spec.securityPurposeFilter && purpose !in CCTV_SECURITY_PURPOSES) {
            return null
        }
        return FacilityPoint(
            type = spec.type,
            lat = geo.lat,
            lng = geo.lng,
            name = row.field(columns.name),
            purpose = purpose,
            cameraCount = row.field(columns.cameraCount)?.toIntOrNull()?.takeIf { it > 0 },
            address = row.field(columns.address),
            phone = row.field(columns.phone),
        )
    }

    private fun readGeoPoint(row: List<String>, columns: CsvColumns, spec: FacilityCsvSpec): GeoPoint? {
        if (columns.useWgs84) {
            return GeoPoint.orNull(
                row.field(columns.lat)?.toDoubleOrNull(),
                row.field(columns.lng)?.toDoubleOrNull(),
            )
        }
        return projectXy(
            row.field(columns.x)?.toDoubleOrNull(),
            row.field(columns.y)?.toDoubleOrNull(),
            spec.coordSystem,
        )
    }

    private fun projectXy(x: Double?, y: Double?, coordSystem: FacilityCoordSystem): GeoPoint? {
        if (x == null || y == null) {
            return null
        }
        val wgs: Wgs84Point? = when (coordSystem) {
            FacilityCoordSystem.EPSG5179 -> Epsg5179.toWgs84(x, y)
            FacilityCoordSystem.EPSG5174 -> Epsg5174.toWgs84(x, y)
            FacilityCoordSystem.WGS84 -> null
        }
        return GeoPoint.orNull(wgs?.lat, wgs?.lng)
    }

    private fun buildHeaderIndex(header: List<String>): Map<String, Int> {
        val map: LinkedHashMap<String, Int> = LinkedHashMap<String, Int>()
        header.forEachIndexed { index, raw ->
            val key: String = raw.trim()
            if (key.isNotEmpty() && !map.containsKey(key)) {
                map[key] = index
            }
        }
        return map
    }

    private fun firstIndexOf(headerIndex: Map<String, Int>, candidates: List<String>): Int? =
        candidates.firstNotNullOfOrNull { headerIndex[it] }

    private fun List<String>.field(index: Int?): String? {
        if (index == null || index >= size) {
            return null
        }
        return this[index].trim().takeIf { it.isNotEmpty() }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return decodeStrict(bytes, Charsets.UTF_8) ?: String(bytes, CP949)
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: Exception) {
        null
    }

    private fun tokenizeCsv(text: String): List<List<String>> {
        val records: ArrayList<List<String>> = ArrayList<List<String>>()
        var record: ArrayList<String> = ArrayList<String>()
        val field: StringBuilder = StringBuilder()
        var inQuotes: Boolean = false
        var i: Int = 0
        val n: Int = text.length
        while (i < n) {
            val c: Char = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        record.add(field.toString())
                        field.setLength(0)
                    }
                    '\n' -> {
                        record.add(field.toString())
                        records.add(record)
                        record = ArrayList()
                        field.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 >= n || text[i + 1] != '\n') {
                            record.add(field.toString())
                            records.add(record)
                            record = ArrayList()
                            field.setLength(0)
                        }
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record.add(field.toString())
            records.add(record)
        }
        return records
    }

    private companion object {
        val CP949: Charset = Charset.forName("MS949")
    }
}
