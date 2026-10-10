package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File

@Component
class SafetySpotCsvFileStore(
    private val uploadStore: SafetySpotUploadStore,
    @param:Value("\${bueongi.facility.cctv-csv:data/source/cctv/CCTV정보.csv}") private val cctvCsvPath: String,
    @param:Value("\${bueongi.facility.lamp-csv-dir:data/source/lamp}") private val lampCsvDir: String,
    @param:Value("\${bueongi.facility.bell-csv-dir:data/source/bell}") private val bellCsvDir: String,
    @param:Value("\${bueongi.facility.police-csv-dir:data/source/police}") private val policeCsvDir: String,
) {
    fun list(): List<SafetySpotCsvFile> {
        val files: ArrayList<SafetySpotCsvFile> = ArrayList()
        val cctv: File? = resolveCctvCsvFile(cctvCsvPath)
        if (cctv != null) {
            files.add(sourceFile(cctv, SafetySpotType.CCTV))
        }
        addSourceDir(files, resolveCsvDir(lampCsvDir, DEFAULT_LAMP_CSV_DIR), SafetySpotType.SECURITY_LAMP)
        addSourceDir(files, resolveCsvDir(bellCsvDir, bellCsvDir), SafetySpotType.BELL)
        addSourceDir(files, resolveCsvDir(policeCsvDir, policeCsvDir), SafetySpotType.POLICE)
        for (original in uploadStore.originalFiles()) {
            val cdnType: String = original.name.removeSuffix(".csv")
            val type: SafetySpotType = SafetySpotType.ofCdnType(cdnType) ?: continue
            files.add(uploadFile(original, type))
        }
        return files
    }

    fun readBytes(file: SafetySpotCsvFile): ByteArray = file.file.readBytes()

    fun readSourcePage(file: SafetySpotCsvFile, page: Int, size: Int, skippedOnly: Boolean, keyword: String): CsvPage {
        val builder = CsvPageBuilder(page, size, skippedOnly, keyword)
        openCsv(file.file).use { reader ->
            val headerLine: String = reader.readLine() ?: return builder.build(emptyList())
            val lampColumns: LampCsvColumns? = LampCsvColumns.of(headerLine)
            var rowNumber: Int = 0
            for (line in reader.lineSequence()) {
                if (line.isBlank()) {
                    continue
                }
                rowNumber++
                val result: CsvRowResult = judgeSourceLine(file, lampColumns, line)
                if (builder.count(result, line)) {
                    builder.add(CsvRow(rowNumber, splitCsvLine(line), result))
                }
            }
            return builder.build(splitCsvLine(headerLine.removePrefix("\uFEFF")))
        }
    }

    private fun judgeSourceLine(file: SafetySpotCsvFile, lampColumns: LampCsvColumns?, line: String): CsvRowResult {
        if (!file.readByServer) {
            return CsvRowResult.NotRead
        }
        if (file.type == SafetySpotType.CCTV) {
            return judgeCctvCsvLine(line)
        }
        if (lampColumns == null) {
            return CsvRowResult.Skipped(CsvSkipReason.NO_COORDINATE_COLUMNS)
        }
        return lampColumns.judge(line)
    }

    private fun addSourceDir(files: MutableList<SafetySpotCsvFile>, dir: File?, type: SafetySpotType) {
        if (dir == null) {
            return
        }
        for (file in dir.listCsvFiles()) {
            files.add(sourceFile(file, type))
        }
    }

    // 서버가 켜질 때 직접 읽는 원천은 CCTV·보안등뿐이다(SafetySpotRepository.load).
    private fun sourceFile(file: File, type: SafetySpotType): SafetySpotCsvFile {
        val readByServer: Boolean = type == SafetySpotType.CCTV || type == SafetySpotType.SECURITY_LAMP
        return SafetySpotCsvFile(
            origin = SafetySpotCsvOrigin.SOURCE,
            type = type,
            name = file.name,
            sizeBytes = file.length(),
            modifiedAtMs = file.lastModified(),
            readByServer = readByServer,
            replacedByUpload = uploadStore.hasUpload(type),
            file = file,
        )
    }

    private fun uploadFile(file: File, type: SafetySpotType): SafetySpotCsvFile {
        return SafetySpotCsvFile(
            origin = SafetySpotCsvOrigin.UPLOAD,
            type = type,
            name = file.name,
            sizeBytes = file.length(),
            modifiedAtMs = file.lastModified(),
            readByServer = true,
            replacedByUpload = false,
            file = file,
        )
    }
}

private fun splitCsvLine(line: String): List<String> {
    val cells: ArrayList<String> = ArrayList()
    val cell = StringBuilder()
    var inQuotes: Boolean = false
    var at: Int = 0
    while (at < line.length) {
        val char: Char = line[at]
        val isEscapedQuote: Boolean = inQuotes && char == '"' && at + 1 < line.length && line[at + 1] == '"'
        if (isEscapedQuote) {
            cell.append('"')
            at++
        } else if (char == '"') {
            inQuotes = !inQuotes
        } else if (char == ',' && !inQuotes) {
            cells.add(cell.toString())
            cell.setLength(0)
        } else {
            cell.append(char)
        }
        at++
    }
    cells.add(cell.toString())
    return cells
}
