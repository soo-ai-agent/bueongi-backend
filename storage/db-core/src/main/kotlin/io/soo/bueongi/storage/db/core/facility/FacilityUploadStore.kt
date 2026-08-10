package io.soo.bueongi.storage.db.core.facility

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.FacilityType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 저장 계약: `{uploadDir}/{cdnType}.json` — CDN 컬렉션과 동형 JSON 이 재기동을 넘어 되읽히므로 DTO 필드 리네임 금지. */
@Component
class FacilityUploadStore(
    objectMapper: ObjectMapper,
    @Value("\${bueongi.facility.upload-dir:./data/facility-uploads}") private val uploadDir: String,
) {
    private val log: Logger = LoggerFactory.getLogger(FacilityUploadStore::class.java)

    private val writeMapper: ObjectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun save(type: FacilityType, points: List<FacilityPoint>) {
        val dir: File = File(uploadDir)
        Files.createDirectories(dir.toPath())
        val target: File = File(dir, "${type.cdnType}.json")
        val collection: FacilityCollectionDto = FacilityCollectionDto(
            type = type.cdnType,
            items = points.map { it.toItemDto() },
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

    fun findByType(type: FacilityType): List<FacilityPoint>? {
        val file: File = File(uploadDir, "${type.cdnType}.json")
        if (!file.isFile) {
            return null
        }
        return try {
            val collection: FacilityCollectionDto = writeMapper.readValue(file, FacilityCollectionDto::class.java)
            collection.items.mapNotNull { it.toFacilityPoint(type) }
        } catch (e: Exception) {
            log.warn("시설 업로드 파일 읽기 실패(무시하고 CDN/CSV 폴백): {} ({})", file.path, e.message)
            null
        }
    }
}

private fun FacilityPoint.toItemDto(): FacilityItemDto = FacilityItemDto(
    name = name,
    lat = lat,
    lng = lng,
    sigunguCode = sigungu,
    address = address,
    phone = phone,
    properties = if (purpose != null || cameraCount != null) {
        FacilityItemPropertiesDto(purpose = purpose, cameraCount = cameraCount)
    } else {
        null
    },
)
