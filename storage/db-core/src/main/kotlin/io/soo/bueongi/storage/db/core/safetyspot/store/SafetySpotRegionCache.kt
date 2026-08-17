package io.soo.bueongi.storage.db.core.safetyspot.store

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotCollectionDto
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 필요할 때 받아 온 지역 데이터를 지역 단위로 남긴다.
 *
 * 저장 계약: `{regionDir}/{종류}/{지역코드}.json` — CDN 컬렉션과 동형이라 재기동 때 그대로 되읽는다.
 * 파일을 지역마다 나누는 이유는 한 지역을 다시 받아도 다른 지역이 영향을 받지 않게 하기 위해서다.
 */
@Component
class SafetySpotRegionCache(
    objectMapper: ObjectMapper,
    @param:Value("\${bueongi.facility.region-cache-dir:./data/region-cache}")
    private val regionDir: String,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotRegionCache::class.java)

    private val writeMapper: ObjectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    private val readMapper: ObjectMapper = objectMapper

    fun isCached(type: SafetySpotType, regionCode: String): Boolean =
        fileOf(type, regionCode).isFile

    fun save(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        val target: File = fileOf(type, regionCode)
        Files.createDirectories(target.parentFile.toPath())
        val collection = SafetySpotCollectionDto(type = type.cdnType, items = points.map { it.toItemDto() })
        val temp: File = File.createTempFile("$regionCode-", ".json.tmp", target.parentFile)
        try {
            writeMapper.writeValue(temp, collection)
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            log.info("지역 캐시 저장: type={}, region={}, count={}", type, regionCode, points.size)
        } finally {
            temp.delete()
        }
    }

    /** 재기동 때 전 지역을 되읽는다. 깨진 파일은 건너뛰고 나머지를 살린다. */
    fun loadAll(type: SafetySpotType): List<SafetySpotPoint> {
        val dir = File(regionDir, type.cdnType)
        val files: List<File> = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
        val points: ArrayList<SafetySpotPoint> = ArrayList()
        for (file in files.sortedBy { it.name }) {
            try {
                val collection: SafetySpotCollectionDto = readMapper.readValue(file, SafetySpotCollectionDto::class.java)
                for (item in collection.items) {
                    val point: SafetySpotPoint = item.toSafetySpotPoint(type) ?: continue
                    points.add(point)
                }
            } catch (e: Exception) {
                log.warn("지역 캐시 파일을 읽지 못했습니다(건너뜀): {} ({})", file.path, e.message)
            }
        }
        return points
    }

    fun cachedRegions(type: SafetySpotType): Set<String> =
        File(regionDir, type.cdnType).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?.toSet()
            .orEmpty()

    private fun fileOf(type: SafetySpotType, regionCode: String): File =
        File(File(regionDir, type.cdnType), "$regionCode.json")
}
