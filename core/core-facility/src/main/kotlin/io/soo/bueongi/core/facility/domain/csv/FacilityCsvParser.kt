package io.soo.bueongi.core.facility.domain.csv

import io.soo.bueongi.core.enums.FacilityCoordSystem

import io.soo.bueongi.support.util.Wgs84Point
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

        val columns: CsvColumns = spec.locateColumns(buildHeaderIndex(records[headerPos]))
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

    /** 좌표가 없거나 방범 목적이 아닌 행은 버린다(null). */
    private fun toFacilityPoint(row: List<String>, columns: CsvColumns, spec: FacilityCsvSpec): FacilityPoint? {
        val geo: GeoPoint = readGeoPoint(row, columns, spec) ?: return null
        val purpose: String? = columns.purpose(row)
        if (spec.securityPurposeFilter && purpose !in CCTV_SECURITY_PURPOSES) {
            return null
        }
        return FacilityPoint(
            type = spec.type,
            lat = geo.lat,
            lng = geo.lng,
            name = columns.name(row),
            purpose = purpose,
            cameraCount = columns.cameraCount(row),
            address = columns.address(row),
            phone = columns.phone(row),
        )
    }

    private fun readGeoPoint(row: List<String>, columns: CsvColumns, spec: FacilityCsvSpec): GeoPoint? {
        if (columns.useWgs84) {
            return GeoPoint.orNull(
                columns.latitude(row),
                columns.longitude(row),
            )
        }
        return projectXy(
            columns.projectedX(row),
            columns.projectedY(row),
            spec.coordSystem,
        )
    }

    private fun projectXy(x: Double?, y: Double?, coordSystem: FacilityCoordSystem): GeoPoint? {
        if (x == null || y == null) {
            return null
        }
        val wgs: Wgs84Point? = coordSystem.toWgs84(x, y)
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

    /**
     * CSV 를 행×열로 쪼갠다(RFC 4180 기본형).
     *
     * 라이브러리를 쓰지 않는 이유: 공공데이터 CSV 는 **주소에 콤마가 들어간 따옴표 필드**와
     * CRLF/LF/CR 이 섞인 줄바꿈이 함께 나오는데, 이 두 가지만 처리하면 되고
     * 인코딩 판별([decode])은 이미 우리가 하고 있어 의존성을 늘릴 이유가 없다.
     *
     * 처리 규칙: `"` 안에서는 콤마·줄바꿈이 글자로 취급되고, `""` 는 따옴표 한 글자다.
     */
    private fun tokenizeCsv(text: String): List<List<String>> {
        val records: ArrayList<List<String>> = ArrayList<List<String>>()
        var record: ArrayList<String> = ArrayList<String>()
        val field: StringBuilder = StringBuilder()
        var inQuotes: Boolean = false
        var cursor: Int = 0
        val length: Int = text.length
        while (cursor < length) {
            val char: Char = text[cursor]
            if (inQuotes) {
                if (char == '"') {
                    val isEscapedQuote: Boolean = cursor + 1 < length && text[cursor + 1] == '"'
                    if (isEscapedQuote) {
                        field.append('"')
                        cursor++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(char)
                }
            } else {
                when (char) {
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
                        // CRLF 는 뒤따르는 \n 이 행을 끊는다. 여기서 끊으면 빈 행이 하나 더 생긴다.
                        val isCrLf: Boolean = cursor + 1 < length && text[cursor + 1] == '\n'
                        if (!isCrLf) {
                            record.add(field.toString())
                            records.add(record)
                            record = ArrayList()
                            field.setLength(0)
                        }
                    }
                    else -> field.append(char)
                }
            }
            cursor++
        }
        // 마지막 줄에 줄바꿈이 없는 파일이 흔하다 — 남은 내용을 마지막 행으로 넣는다.
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
