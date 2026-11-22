package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File

// 서버는 CSV 를 직접 적재하지 않는다(관리자 업로드로 일원화, 2026-10-11).
// 여기는 보관된 원본 CSV 와 업로드 원본을 찾아 목록·바이트로 내줄 뿐이고, 행 판정은 core 의 시설 계약이 한다.
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
        val cctv: File? = resolveCctv(cctvCsvPath)
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

    fun readBytes(file: SafetySpotCsvFile): ByteArray {
        return file.file.readBytes()
    }

    private fun addSourceDir(files: MutableList<SafetySpotCsvFile>, dir: File?, type: SafetySpotType) {
        if (dir == null) {
            return
        }
        for (file in dir.listCsvFiles()) {
            files.add(sourceFile(file, type))
        }
    }

    private fun sourceFile(file: File, type: SafetySpotType): SafetySpotCsvFile {
        return SafetySpotCsvFile(
            origin = SafetySpotCsvOrigin.SOURCE,
            type = type,
            name = file.name,
            sizeBytes = file.length(),
            modifiedAtMs = file.lastModified(),
            readByServer = false,
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

    private fun resolveCctv(configured: String): File? {
        if (configured.isBlank()) {
            return null
        }
        val candidates: List<String> =
            listOf(
                configured,
                "$configured.gz",
                DEFAULT_CCTV_CSV,
                "$DEFAULT_CCTV_CSV.gz",
                "../backend/$DEFAULT_CCTV_CSV",
                "backend/$DEFAULT_CCTV_CSV",
            )
        for (candidate in candidates) {
            val file: File = File(candidate)
            if (file.isFile) {
                return file.absoluteFile
            }
        }
        return null
    }

    private fun resolveCsvDir(configured: String, default: String): File? {
        if (configured.isBlank()) {
            return null
        }
        for (candidate in listOf(configured, default)) {
            val dir: File = File(candidate)
            if (dir.isDirectory) {
                return dir
            }
        }
        return null
    }

    private companion object {
        const val DEFAULT_CCTV_CSV: String = "data/source/cctv/CCTV정보.csv"
        const val DEFAULT_LAMP_CSV_DIR: String = "data/source/lamp"
    }
}
