package io.soo.bueongi.storage.db.core.safetyspot.store

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotCollectionDto
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotItemDto
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotItemPropertiesDto
import io.soo.bueongi.storage.db.core.safetyspot.model.CctvSpot
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.phoneOrNull
import io.soo.bueongi.storage.db.core.safetyspot.model.regionCodeOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock

// 저장 계약: `{uploadDir}/{cdnType}.json` — CDN 컬렉션과 동형 JSON 이라 DTO 필드 리네임 금지.
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
        val dir = File(uploadDir)
        Files.createDirectories(dir.toPath())
        val target = File(dir, "${type.cdnType}.json")
        val collection =
            SafetySpotCollectionDto(
                type = type.cdnType,
                items = points.map { it.toItemDto() },
                savedAt = clock.instant().toString(),
            )
        val temp: File = File.createTempFile("${type.cdnType}-", ".json.tmp", dir)
        try {
            writeMapper.writeValue(temp, collection)
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } finally {
            temp.delete()
        }
        log.info("시설 업로드 저장: type={}, count={}, file={}", type.cdnType, points.size, target.path)
    }

    fun saveOriginal(type: SafetySpotType, bytes: ByteArray) {
        val dir = File(uploadDir, ORIGINALS_DIR)
        Files.createDirectories(dir.toPath())
        val target = File(dir, "${type.cdnType}.csv")
        val temp: File = File.createTempFile("${type.cdnType}-", ".csv.tmp", dir)
        try {
            Files.write(temp.toPath(), bytes)
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } finally {
            temp.delete()
        }
    }

    fun originalFiles(): List<File> {
        val dir = File(uploadDir, ORIGINALS_DIR)
        return dir.listCsvFiles()
    }

    fun hasUpload(type: SafetySpotType): Boolean = File(uploadDir, "${type.cdnType}.json").isFile

    fun findByType(type: SafetySpotType): List<SafetySpotPoint>? {
        val file = File(uploadDir, "${type.cdnType}.json")
        if (!file.isFile) {
            return null
        }
        return try {
            val collection: SafetySpotCollectionDto = writeMapper.readValue(file, SafetySpotCollectionDto::class.java)
            collection.items.mapNotNull { it.toSafetySpotPoint(type) }
        } catch (e: Exception) {
            log.warn("시설 업로드 파일 읽기 실패(무시하고 CDN/CSV 폴백): {} ({})", file.path, e.message)
            null
        }
    }

    private companion object {
        const val ORIGINALS_DIR: String = "originals"
    }
}

// 설치 목적·대수는 CCTV 만 갖는다 — 그 종류일 때만 칸을 만든다.
private fun SafetySpotPoint.cctvPropertiesOrNull(): SafetySpotItemPropertiesDto? {
    if (this !is CctvSpot) {
        return null
    }
    if (purpose == null && cameraCount == null) {
        return null
    }
    return SafetySpotItemPropertiesDto(purpose = purpose, cameraCount = cameraCount)
}

internal fun SafetySpotPoint.toItemDto(): SafetySpotItemDto =
    SafetySpotItemDto(
        name = name,
        lat = lat,
        lng = lng,
        sigunguCode = regionCodeOrNull(),
        address = address,
        phone = phoneOrNull(),
        properties = cctvPropertiesOrNull(),
    )
