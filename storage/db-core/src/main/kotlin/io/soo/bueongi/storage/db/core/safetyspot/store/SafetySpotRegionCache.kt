package io.soo.bueongi.storage.db.core.safetyspot.store

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotCollectionDto
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// 저장 계약: `{regionDir}/{종류}/{지역코드}.json` — CDN 컬렉션과 동형이라 재기동 때 그대로 되읽는다.
@Component
class SafetySpotRegionCache(
    objectMapper: ObjectMapper,
    @param:Value("\${bueongi.facility.region-cache-dir:./data/region-cache}")
    private val regionDir: String,
    @param:Value("\${bueongi.facility.region-cache-ttl-days:90}")
    private val ttlDays: Long = DEFAULT_TTL_DAYS,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotRegionCache::class.java)

    private val writeMapper: ObjectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    private val readMapper: ObjectMapper = objectMapper

    private val savedAtByFile: ConcurrentHashMap<String, Instant> = ConcurrentHashMap()

    fun isCached(type: SafetySpotType, regionCode: String): Boolean {
        val savedAt: Instant = savedAtOf(type, regionCode) ?: return false
        return Duration.between(savedAt, clock.instant()) < Duration.ofDays(ttlDays)
    }

    fun savedAtOf(type: SafetySpotType, regionCode: String): Instant? {
        val remembered: Instant? = savedAtByFile[cacheKey(type, regionCode)]
        if (remembered != null) {
            return remembered
        }
        val file: File = fileOf(type, regionCode)
        if (!file.isFile) {
            return null
        }
        return try {
            val collection: SafetySpotCollectionDto = readMapper.readValue(file, SafetySpotCollectionDto::class.java)
            val savedAt: Instant? = collection.savedAt?.let { Instant.parse(it) }
            if (savedAt != null) {
                savedAtByFile[cacheKey(type, regionCode)] = savedAt
            }
            savedAt
        } catch (e: Exception) {
            log.warn("지역 캐시 저장 시각을 읽지 못했습니다(다시 받음): {} ({})", file.path, e.message)
            null
        }
    }

    fun save(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        val target: File = fileOf(type, regionCode)
        Files.createDirectories(target.parentFile.toPath())
        val savedAt: Instant = clock.instant()
        val collection = SafetySpotCollectionDto(
            type = type.cdnType,
            items = points.map { it.toItemDto() },
            savedAt = savedAt.toString(),
        )
        val temp: File = File.createTempFile("$regionCode-", ".json.tmp", target.parentFile)
        try {
            writeMapper.writeValue(temp, collection)
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            savedAtByFile[cacheKey(type, regionCode)] = savedAt
            log.info("지역 캐시 저장: type={}, region={}, count={}", type, regionCode, points.size)
        } finally {
            temp.delete()
        }
    }

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

    private fun cacheKey(type: SafetySpotType, regionCode: String): String =
        "${type.cdnType}/$regionCode"

    private fun fileOf(type: SafetySpotType, regionCode: String): File =
        File(File(regionDir, type.cdnType), "$regionCode.json")

    private companion object {
        // 지자체가 뒤늦게 좌표를 채워 넣는 일이 있어 분기마다 한 번은 다시 받는다.
        const val DEFAULT_TTL_DAYS: Long = 90
    }
}
