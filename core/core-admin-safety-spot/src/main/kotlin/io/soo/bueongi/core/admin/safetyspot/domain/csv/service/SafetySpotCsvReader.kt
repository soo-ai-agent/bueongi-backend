package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.CsvColumns
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.RegionCodeFinder
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvReadResult
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.safetyspot.store.CsvPageBuilder
import io.soo.bueongi.storage.db.safetyspot.store.CsvRow
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowLoaded
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowResult
import io.soo.bueongi.support.util.CP949
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

@Component
class SafetySpotCsvReader {
    fun readPoints(
        bytes: ByteArray,
        format: SafetySpotCsvFormat,
        regionCodeFinder: RegionCodeFinder? = null,
    ): SafetySpotCsvReadResult {
        val rows: List<List<String>> = readRows(bytes)
        if (rows.isEmpty()) {
            return SafetySpotCsvReadResult(format.coordSystem, received = 0, points = emptyList())
        }
        val header: List<String> = rows.first()
        val dataRows: List<List<String>> = rows.subList(1, rows.size)
        val columns: CsvColumns = format.findColumns(header)

        val points: ArrayList<SafetySpotPoint> = ArrayList()
        for (row in dataRows) {
            val result: CsvRowResult = format.readRow(row, columns, regionCodeFinder)
            if (result is CsvRowLoaded) {
                points.add(result.point)
            }
        }
        return SafetySpotCsvReadResult(columns.coordSystem, received = dataRows.size, points = points)
    }

    fun readPage(
        bytes: ByteArray,
        format: SafetySpotCsvFormat,
        page: Int,
        size: Int,
        skippedOnly: Boolean,
        keyword: String,
    ): CsvPage {
        val pageBuilder = CsvPageBuilder(page, size, skippedOnly, keyword)
        val rows: List<List<String>> = readRows(bytes)
        if (rows.isEmpty()) {
            return pageBuilder.build(emptyList())
        }
        val header: List<String> = rows.first()
        val dataRows: List<List<String>> = rows.subList(1, rows.size)
        val columns: CsvColumns = format.findColumns(header)

        for (index in dataRows.indices) {
            val row: List<String> = dataRows[index]
            val result: CsvRowResult = format.readRow(row, columns, regionCodeFinder = null)
            val isOnPage: Boolean = pageBuilder.count(result, row)
            if (isOnPage) {
                pageBuilder.add(CsvRow(rowNumber = index + 1, cells = row, result = result))
            }
        }
        return pageBuilder.build(header)
    }

    // 칸이 모두 빈 줄은 뺀다. 남은 첫 줄이 머리글이고 그 뒤가 데이터다.
    private fun readRows(bytes: ByteArray): List<List<String>> {
        val text: String = toText(bytes)
        val rows: ArrayList<List<String>> = ArrayList()
        for (row in splitIntoRows(text)) {
            if (hasText(row)) {
                rows.add(row)
            }
        }
        return rows
    }

    private fun hasText(row: List<String>): Boolean {
        for (cell in row) {
            if (cell.isNotBlank()) {
                return true
            }
        }
        return false
    }

    // 공공데이터 CSV 는 UTF-8 과 CP949 가 섞여 온다. UTF-8 로 읽히지 않으면 CP949 로 읽는다.
    private fun toText(bytes: ByteArray): String {
        val csvBytes: ByteArray = unzipIfNeeded(bytes)
        if (startsWithUtf8Mark(csvBytes)) {
            return String(csvBytes, UTF8_MARK_SIZE, csvBytes.size - UTF8_MARK_SIZE, Charsets.UTF_8)
        }
        return try {
            readAsUtf8(csvBytes)
        } catch (_: CharacterCodingException) {
            String(csvBytes, CP949)
        }
    }

    // 엑셀이 UTF-8 파일 맨 앞에 붙이는 표시(BOM, EF BB BF)다. 글자가 아니라서 떼고 읽는다.
    private fun startsWithUtf8Mark(bytes: ByteArray): Boolean {
        return bytes.size >= UTF8_MARK_SIZE &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
    }

    // 깨진 글자를 조용히 다른 글자로 바꾸지 않고 예외를 내게 한다 — 그래야 CP949 파일임을 알아챈다.
    private fun readAsUtf8(bytes: ByteArray): String {
        val decoder: CharsetDecoder =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    // 글자를 하나씩 읽어 줄과 칸으로 나눈다. 따옴표 안의 쉼표와 줄바꿈은 칸을 나누지 않는다.
    private fun splitIntoRows(text: String): List<List<String>> {
        val rows: ArrayList<List<String>> = ArrayList()
        var row: ArrayList<String> = ArrayList()
        val cell = StringBuilder()
        var inQuotes: Boolean = false
        var position: Int = 0
        while (position < text.length) {
            val char: Char = text[position]
            val nextChar: Char? = text.getOrNull(position + 1)
            position++

            if (inQuotes) {
                if (char == '"' && nextChar == '"') {
                    // 따옴표 안에서 따옴표 두 개("")는 따옴표 한 글자다.
                    cell.append('"')
                    position++
                } else if (char == '"') {
                    inQuotes = false
                } else {
                    cell.append(char)
                }
            } else if (char == '"') {
                inQuotes = true
            } else if (char == ',') {
                row.add(cell.toString())
                cell.setLength(0)
            } else if (char == '\n' || char == '\r') {
                // 줄 끝이 \r\n 이면 빈 줄이 하나 더 생기지만, 빈 줄은 readRows 가 뺀다.
                row.add(cell.toString())
                cell.setLength(0)
                rows.add(row)
                row = ArrayList()
            } else {
                cell.append(char)
            }
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }

    private companion object {
        const val UTF8_MARK_SIZE: Int = 3
    }
}
