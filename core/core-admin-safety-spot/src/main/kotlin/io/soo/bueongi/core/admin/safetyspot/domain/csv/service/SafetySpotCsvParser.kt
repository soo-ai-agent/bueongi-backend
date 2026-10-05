package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.CsvColumns
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvParseResult
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvSpec
import io.soo.bueongi.core.enums.SafetySpotCoordSystem
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.CCTV_SECURITY_PURPOSES
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvPageBuilder
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvRow
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvRowResult
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvSkipReason
import io.soo.bueongi.support.util.CP949
import io.soo.bueongi.support.util.Epsg5174
import io.soo.bueongi.support.util.Epsg5179
import io.soo.bueongi.support.util.Wgs84Point
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

@Component
class SafetySpotCsvParser {
    fun parse(bytes: ByteArray, spec: SafetySpotCsvSpec): SafetySpotCsvParseResult {
        val records: List<List<String>> = tokenizeCsv(decode(bytes))
        val headerPos: Int = findHeaderRow(records)
        if (headerPos < 0) {
            return SafetySpotCsvParseResult(spec.coordSystem, received = 0, points = emptyList())
        }

        val columns: CsvColumns = spec.locateColumns(buildHeaderIndex(records[headerPos]))
        val dataRows: List<List<String>> = collectDataRows(records, headerPos)

        val points: ArrayList<SafetySpotPoint> = ArrayList()
        for (row in dataRows) {
            val result: CsvRowResult = judgeRow(row, columns, spec)
            if (result is CsvRowResult.Loaded) {
                points.add(result.point)
            }
        }
        return SafetySpotCsvParseResult(columns.coordSystem, received = dataRows.size, points = points)
    }

    fun readPage(bytes: ByteArray, spec: SafetySpotCsvSpec, page: Int, size: Int, skippedOnly: Boolean, keyword: String): CsvPage {
        val builder = CsvPageBuilder(page, size, skippedOnly, keyword)
        val records: List<List<String>> = tokenizeCsv(decode(bytes))
        val headerPos: Int = findHeaderRow(records)
        if (headerPos < 0) {
            return builder.build(emptyList())
        }

        val columns: CsvColumns = spec.locateColumns(buildHeaderIndex(records[headerPos]))
        val dataRows: List<List<String>> = collectDataRows(records, headerPos)

        for ((index, row) in dataRows.withIndex()) {
            val result: CsvRowResult = judgeRow(row, columns, spec)
            if (builder.count(result, row.joinToString(" "))) {
                builder.add(CsvRow(index + 1, row, result))
            }
        }
        return builder.build(records[headerPos])
    }

    private fun findHeaderRow(records: List<List<String>>): Int {
        for (index in records.indices) {
            if (hasContent(records[index])) {
                return index
            }
        }
        return -1
    }

    private fun collectDataRows(records: List<List<String>>, headerPos: Int): List<List<String>> {
        val rows: ArrayList<List<String>> = ArrayList()
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

    private fun judgeRow(row: List<String>, columns: CsvColumns, spec: SafetySpotCsvSpec): CsvRowResult {
        val geo: GeoPoint = readGeoPoint(row, columns, spec) ?: return CsvRowResult.Skipped(CsvSkipReason.NO_COORDINATE)
        val purpose: String? = columns.purpose(row)
        if (spec.securityPurposeFilter && purpose !in CCTV_SECURITY_PURPOSES) {
            return CsvRowResult.Skipped(CsvSkipReason.NOT_SECURITY_PURPOSE)
        }
        val point: SafetySpotPoint =
            SafetySpotPoint.of(
                type = spec.type,
                lat = geo.lat,
                lng = geo.lng,
                name = columns.name(row),
                purpose = purpose,
                cameraCount = columns.cameraCount(row),
                address = columns.address(row),
                phone = columns.phone(row),
                regionCode = columns.regionCode(row),
            )
        return CsvRowResult.Loaded(point)
    }

    private fun readGeoPoint(row: List<String>, columns: CsvColumns, spec: SafetySpotCsvSpec): GeoPoint? {
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

    private fun projectXy(x: Double?, y: Double?, coordSystem: SafetySpotCoordSystem): GeoPoint? {
        if (x == null || y == null) {
            return null
        }
        val wgs: Wgs84Point? = toWgs84(coordSystem, x, y)
        return GeoPoint.orNull(wgs?.lat, wgs?.lng)
    }

    private fun toWgs84(coordSystem: SafetySpotCoordSystem, x: Double, y: Double): Wgs84Point? {
        if (coordSystem == SafetySpotCoordSystem.EPSG5179) {
            return Epsg5179.toWgs84(x, y)
        }
        if (coordSystem == SafetySpotCoordSystem.EPSG5174) {
            return Epsg5174.toWgs84(x, y)
        }
        return null
    }

    private fun buildHeaderIndex(header: List<String>): Map<String, Int> {
        val map: LinkedHashMap<String, Int> = LinkedHashMap()
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

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }

    private fun tokenizeCsv(text: String): List<List<String>> {
        val records: ArrayList<List<String>> = ArrayList()
        var record: ArrayList<String> = ArrayList()
        val field = StringBuilder()
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
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record.add(field.toString())
            records.add(record)
        }
        return records
    }
}
