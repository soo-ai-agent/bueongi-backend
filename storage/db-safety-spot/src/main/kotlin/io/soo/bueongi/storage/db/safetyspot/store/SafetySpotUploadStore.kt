package io.soo.bueongi.storage.db.safetyspot.store

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.model.StoredSafetySpotCollection
import io.soo.bueongi.storage.db.safetyspot.model.toStoredItem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.time.Clock

// 저장 계약: `{uploadDir}/{cdnType}.json` — CDN 컬렉션과 같은 모양이라 `Stored*` 칸 이름을 바꾸지 않는다.
// 올린 CSV 원본은 `{uploadDir}/originals/{cdnType}.csv` 에 종류마다 최신 하나만 둔다(관리자 CSV 보기용).
@Component
class SafetySpotUploadStore(
    objectMapper: ObjectMapper,
    @param:Value("\${bueongi.facility.upload-dir:./data/facility-uploads}") private val uploadDir: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotUploadStore::class.java)

    private val writeMapper: ObjectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun save(type: SafetySpotType, points: List<SafetySpotPoint>) {
        val dir: File = File(uploadDir)
        Files.createDirectories(dir.toPath())
        val target: File = File(dir, "${type.cdnType}.json")
        val collection: StoredSafetySpotCollection =
            StoredSafetySpotCollection(
                items = points.map { it.toStoredItem() },
                savedAt = clock.instant().toString(),
            )
        target.writeBytesAtomically(writeMapper.writeValueAsBytes(collection))
        log.info("시설 업로드 저장: type={}, count={}, file={}", type.cdnType, points.size, target.path)
    }

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
        return File(uploadDir, "${type.cdnType}.json").isFile
    }

    fun findByType(type: SafetySpotType): List<SafetySpotPoint>? {
        val file: File = File(uploadDir, "${type.cdnType}.json")
        if (!file.isFile) {
            return null
        }
        return try {
            val collection: StoredSafetySpotCollection = writeMapper.readValue(file, StoredSafetySpotCollection::class.java)
            val points: ArrayList<SafetySpotPoint> = ArrayList()
            for (item in collection.items) {
                val point: SafetySpotPoint = item.toSafetySpotPoint(type) ?: continue
                points.add(point)
            }
            points
        } catch (e: Exception) {
            log.warn("시설 업로드 파일 읽기 실패(무시하고 CDN/CSV 폴백): {} | 원인={}", file.path, e.javaClass.simpleName)
            null
        }
    }

    private companion object {
        const val ORIGINALS_DIR: String = "originals"
    }
}
