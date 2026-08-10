package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.safemap.Epsg5174
import io.soo.bueongi.client.safemap.Epsg5179
import io.soo.bueongi.client.safemap.Wgs84Point
import io.soo.bueongi.storage.db.core.facility.CCTV_SECURITY_PURPOSES
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

@Component
class FacilityCsvParser {

    fun parse(bytes: ByteArray, spec: FacilityCsvSpec): FacilityCsvParseResult {
        val text: String = decode(bytes)
        val records: List<List<String>> = tokenizeCsv(text)
        val headerPos: Int = records.indexOfFirst { row -> row.any { it.isNotBlank() } }
        if (headerPos < 0) {
            return FacilityCsvParseResult(spec.coordSystem, received = 0, points = emptyList())
        }

        val headerIndex: Map<String, Int> = buildHeaderIndex(records[headerPos])
        val dataRows: List<List<String>> = records.drop(headerPos + 1)
            .filter { row -> row.any { it.isNotBlank() } }

        val latIdx: Int? = firstIndexOf(headerIndex, spec.latHeaders)
        val lngIdx: Int? = firstIndexOf(headerIndex, spec.lngHeaders)
        val useWgs84: Boolean = latIdx != null && lngIdx != null
        val effectiveCoordSystem: FacilityCoordSystem = if (useWgs84) {
            FacilityCoordSystem.WGS84
        } else {
            spec.coordSystem
        }

        val xIdx: Int? = firstIndexOf(headerIndex, spec.xHeaders)
        val yIdx: Int? = firstIndexOf(headerIndex, spec.yHeaders)
        val nameIdx: Int? = firstIndexOf(headerIndex, spec.nameHeaders)
        val addressIdx: Int? = firstIndexOf(headerIndex, spec.addressHeaders)
        val purposeIdx: Int? = firstIndexOf(headerIndex, spec.purposeHeaders)
        val cameraIdx: Int? = firstIndexOf(headerIndex, spec.cameraCountHeaders)
        val phoneIdx: Int? = firstIndexOf(headerIndex, spec.phoneHeaders)

        val points: List<FacilityPoint> = dataRows.mapNotNull { row ->
            val geo: GeoPoint = if (useWgs84) {
                GeoPoint.orNull(row.field(latIdx)?.toDoubleOrNull(), row.field(lngIdx)?.toDoubleOrNull())
            } else {
                projectXy(row.field(xIdx)?.toDoubleOrNull(), row.field(yIdx)?.toDoubleOrNull(), spec.coordSystem)
            } ?: return@mapNotNull null

            val purpose: String? = row.field(purposeIdx)
            if (spec.securityPurposeFilter && purpose !in CCTV_SECURITY_PURPOSES) {
                return@mapNotNull null
            }

            FacilityPoint(
                type = spec.type,
                lat = geo.lat,
                lng = geo.lng,
                name = row.field(nameIdx),
                purpose = purpose,
                cameraCount = row.field(cameraIdx)?.toIntOrNull()?.takeIf { it > 0 },
                address = row.field(addressIdx),
                phone = row.field(phoneIdx),
            )
        }
        return FacilityCsvParseResult(effectiveCoordSystem, received = dataRows.size, points = points)
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
