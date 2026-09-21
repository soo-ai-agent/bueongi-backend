package io.soo.bueongi.storage.db.core.safetyspot.store

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotCollectionDto
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotItemDto
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotItemPropertiesDto
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock

// 저장 계약: `{uploadDir}/{cdnType}.json` — CDN 컬렉션과 동형 JSON 이라 DTO 필드 리네임 금지.
@Component
class SafetySpotUploadStore(
    objectMapper: ObjectMapper,
    @param:Value("\${bueongi.facility.upload-dir:./data/facility-uploads}") private val uploadDir: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotUploadStore::class.java)

    private val writeMapper: ObjectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun save(
        type: SafetySpotType,
        points: List<SafetySpotPoint>,
    ) {
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
}

internal fun SafetySpotPoint.toItemDto(): SafetySpotItemDto =
    SafetySpotItemDto(
        name = name,
        lat = lat,
        lng = lng,
        sigunguCode = sigungu,
        address = address,
        phone = phone,
        properties =
            if (purpose != null || cameraCount != null) {
                SafetySpotItemPropertiesDto(purpose = purpose, cameraCount = cameraCount)
            } else {
                null
            },
    )
