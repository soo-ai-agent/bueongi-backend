package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files

// 올린 CSV 원본을 `{uploadDir}/originals/{cdnType}.csv` 에 종류마다 최신 하나만 둔다(관리자 CSV 보기용).
// 시설 자체는 DB(safety_spot, source='upload')에 있다.
@Component
class SafetySpotUploadStore(
    @param:Value("\${bueongi.facility.upload-dir:./data/facility-uploads}") private val uploadDir: String,
) {
    fun saveOriginal(type: SafetySpotType, bytes: ByteArray) {
        val dir: File = File(uploadDir, ORIGINALS_DIR)
        Files.createDirectories(dir.toPath())
        val target: File = File(dir, "${type.cdnType}.csv")
        target.writeBytesAtomically(bytes)
    }

    fun originalFiles(): List<File> {
        val dir: File = File(uploadDir, ORIGINALS_DIR)
        return dir.listCsvFiles()
    }

    fun hasUpload(type: SafetySpotType): Boolean {
        return File(File(uploadDir, ORIGINALS_DIR), "${type.cdnType}.csv").isFile
    }

    private companion object {
        const val ORIGINALS_DIR: String = "originals"
    }
}
