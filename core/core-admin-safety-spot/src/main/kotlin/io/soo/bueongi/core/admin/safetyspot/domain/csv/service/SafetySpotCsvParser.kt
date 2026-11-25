package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.CsvColumns
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvContract
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvParseResult
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.safetyspot.store.CsvPageBuilder
import io.soo.bueongi.storage.db.safetyspot.store.CsvRow
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowLoaded
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowResult
import io.soo.bueongi.support.util.CP949
import org.springframework.stereotype.Component
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

@Component
class SafetySpotCsvParser {
    fun parse(bytes: ByteArray, contract: SafetySpotCsvContract): SafetySpotCsvParseResult {
        val records: List<List<String>> = tokenizeCsv(decode(bytes))
        val headerPos: Int = findHeaderRow(records)
        if (headerPos < 0) {
            return SafetySpotCsvParseResult(contract.coordSystem, received = 0, points = emptyList())
        }

        val columns: CsvColumns = contract.locateColumns(buildHeaderIndex(records[headerPos]))
        val dataRows: List<List<String>> = collectDataRows(records, headerPos)

        val points: ArrayList<SafetySpotPoint> = ArrayList()
        for (row in dataRows) {
            val result: CsvRowResult = contract.judge(row, columns)
            if (result is CsvRowLoaded) {
                points.add(result.point)
            }
        }
        return SafetySpotCsvParseResult(columns.coordSystem, received = dataRows.size, points = points)
    }

    fun readPage(bytes: ByteArray, contract: SafetySpotCsvContract, page: Int, size: Int, skippedOnly: Boolean, keyword: String): CsvPage {
        val builder = CsvPageBuilder(page, size, skippedOnly, keyword)
        val records: List<List<String>> = tokenizeCsv(decode(bytes))
        val headerPos: Int = findHeaderRow(records)
        if (headerPos < 0) {
            return builder.build(emptyList())
        }

        val columns: CsvColumns = contract.locateColumns(buildHeaderIndex(records[headerPos]))
        val dataRows: List<List<String>> = collectDataRows(records, headerPos)

        for ((index, row) in dataRows.withIndex()) {
            val result: CsvRowResult = contract.judge(row, columns)
            if (builder.count(result, row)) {
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

    private fun buildHeaderIndex(header: List<String>): Map<String, Int> {
        val map: LinkedHashMap<String, Int> = LinkedHashMap()
        for ((index, raw) in header.withIndex()) {
            val key: String = raw.trim()
            if (key.isNotEmpty() && !map.containsKey(key)) {
                map[key] = index
            }
        }
        return map
    }

    private fun decode(bytes: ByteArray): String {
        val plain: ByteArray = gunzipIfNeeded(bytes)
        if (plain.size >= 3 && plain[0] == 0xEF.toByte() && plain[1] == 0xBB.toByte() && plain[2] == 0xBF.toByte()) {
            return String(plain, 3, plain.size - 3, Charsets.UTF_8)
        }
        return try {
            decodeStrict(plain, Charsets.UTF_8)
        } catch (_: CharacterCodingException) {
            String(plain, CP949)
        }
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String =
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()

    private fun tokenizeCsv(text: String): List<List<String>> {
        val records: ArrayList<List<String>> = ArrayList()
        var record: ArrayList<String> = ArrayList()
        val field = StringBuilder()
        var inQuotes: Boolean = false
        var cursor: Int = 0
        val length: Int = text.length
        while (cursor < length) {
            val char: Char = text[cursor]
            val nextIsQuote: Boolean = cursor + 1 < length && text[cursor + 1] == '"'
            val nextIsNewline: Boolean = cursor + 1 < length && text[cursor + 1] == '\n'
            when {
                inQuotes && char == '"' && nextIsQuote -> {
                    field.append('"')
                    cursor++
                }
                inQuotes && char == '"' -> inQuotes = false
                inQuotes -> field.append(char)
                char == '"' -> inQuotes = true
                char == ',' -> {
                    record.add(field.toString())
                    field.setLength(0)
                }
                char == '\n' -> {
                    record.add(field.toString())
                    records.add(record)
                    record = ArrayList()
                    field.setLength(0)
                }
                // CRLF 는 뒤따르는 \n 이 행을 끊는다. 여기서 끊으면 빈 행이 하나 더 생긴다.
                char == '\r' && nextIsNewline -> {}
                char == '\r' -> {
                    record.add(field.toString())
                    records.add(record)
                    record = ArrayList()
                    field.setLength(0)
                }
                else -> field.append(char)
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
