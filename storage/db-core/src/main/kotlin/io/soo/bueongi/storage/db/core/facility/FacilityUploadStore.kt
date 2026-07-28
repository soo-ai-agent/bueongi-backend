package io.soo.bueongi.storage.db.core.facility

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.FacilityType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 관리자 업로드분 시설 데이터를 디스크에 영속하고 부팅 시 되읽는 저장소.
 *
 * 종류별로 `{uploadDir}/{cdnType}.json`(CDN 컬렉션과 동형: `{type, items:[{lat,lng,name,address,phone,
 * properties:{purpose,cameraCount}}]}`)에 저장한다. [FacilityRepository] 가 부팅 적재 시 이 파일이 있으면
 * 해당 종류를 통째로 교체하므로(업로드 우선), 재기동해도 마지막 업로드가 유지된다.
 *
 * 저장은 원자적이다 — 임시 파일에 쓴 뒤 rename(같은 디렉터리, ATOMIC_MOVE) 해서, 큰 파일을 쓰다 죽어도
 * 반쪽짜리 파일이 남지 않는다.
 */
@Component
class FacilityUploadStore(
    objectMapper: ObjectMapper,
    @Value("\${bueongi.facility.upload-dir:./data/facility-uploads}") private val uploadDir: String,
) {
    private val log = LoggerFactory.getLogger(FacilityUploadStore::class.java)

    // 업로드 파일은 null 필드를 생략해 컴팩트하게 쓴다(대용량 CCTV 대비). 주입 mapper 를 건드리지 않도록 독립 사본을 만든다.
    private val writeMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    /** [type] 업로드분을 `{uploadDir}/{cdnType}.json` 에 원자적으로 저장한다. */
    fun save(type: FacilityType, points: List<FacilityPoint>) {
        val dir: File = File(uploadDir)
        Files.createDirectories(dir.toPath())
        val target: File = File(dir, "${type.cdnType}.json")
        val collection = FacilityCollectionDto(
            type = type.cdnType,
            items = points.map { it.toItemDto() },
        )
        val temp = File.createTempFile("${type.cdnType}-", ".json.tmp", dir)
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

    /** [type] 업로드 파일이 있으면 그 시설 목록을, 없거나 파싱 실패면 null(부팅 적재가 CDN/CSV 로 폴백). */
    fun findByType(type: FacilityType): List<FacilityPoint>? {
        val file: File = File(uploadDir, "${type.cdnType}.json")
        if (!file.isFile) {
            return null
        }
        return try {
            val collection = writeMapper.readValue(file, FacilityCollectionDto::class.java)
            collection.items.mapNotNull { it.toFacilityPoint(type) }
        } catch (e: Exception) {
            log.warn("시설 업로드 파일 읽기 실패(무시하고 CDN/CSV 폴백): {} ({})", file.path, e.message)
            null
        }
    }
}

/** 저장 모델 → CDN 컬렉션 아이템 DTO(왕복 재적재용). purpose/cameraCount 는 properties 로 감싼다. */
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
