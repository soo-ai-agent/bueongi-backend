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
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

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
            val collection: StoredSafetySpotCollection = readMapper.readValue(file, StoredSafetySpotCollection::class.java)
            val savedAtText: String? = collection.savedAt
            var savedAt: Instant? = null
            if (savedAtText != null) {
                val parsed: Instant = Instant.parse(savedAtText)
                savedAtByFile[cacheKey(type, regionCode)] = parsed
                savedAt = parsed
            }
            savedAt
        } catch (e: Exception) {
            log.warn("지역 캐시 저장 시각을 읽지 못했습니다(다시 받음): {} | 원인={}", file.path, e.javaClass.simpleName)
            null
        }
    }

    fun save(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        val target: File = fileOf(type, regionCode)
        Files.createDirectories(target.parentFile.toPath())
        // 다시 받은 결과가 비어도 전에 받은 API 데이터는 지우지 않는다 — 저장 시각만 새로 해 곧바로 다시 받지 않게 한다.
        var kept: List<SafetySpotPoint> = points
        if (points.isEmpty()) {
            kept = load(type, regionCode)
        }
        val savedAt: Instant = clock.instant()
        val collection: StoredSafetySpotCollection =
            StoredSafetySpotCollection(
                items = kept.map { it.toStoredItem() },
                savedAt = savedAt.toString(),
            )
        target.writeBytesAtomically(writeMapper.writeValueAsBytes(collection))
        savedAtByFile[cacheKey(type, regionCode)] = savedAt
        log.info("지역 캐시 저장: type={}, region={}, count={}", type, regionCode, kept.size)
    }

    fun load(type: SafetySpotType, regionCode: String): List<SafetySpotPoint> {
        val file: File = fileOf(type, regionCode)
        if (!file.isFile) {
            return emptyList()
        }
        val points: ArrayList<SafetySpotPoint> = ArrayList()
        try {
            val collection: StoredSafetySpotCollection = readMapper.readValue(file, StoredSafetySpotCollection::class.java)
            for (item in collection.items) {
                val point: SafetySpotPoint = item.toSafetySpotPoint(type) ?: continue
                points.add(point)
            }
        } catch (e: Exception) {
            log.warn("지역 캐시 파일을 읽지 못했습니다(건너뜀): {} | 원인={}", file.path, e.javaClass.simpleName)
        }
        return points
    }

    fun list(type: SafetySpotType): Set<String> {
        val dir: File = File(regionDir, type.cdnType)
        val regions: LinkedHashSet<String> = LinkedHashSet()
        for (file in dir.listJsonFiles()) {
            regions.add(file.name.removeSuffix(".json"))
        }
        return regions
    }

    private fun cacheKey(type: SafetySpotType, regionCode: String): String {
        return "${type.cdnType}/$regionCode"
    }

    private fun fileOf(type: SafetySpotType, regionCode: String): File {
        return File(File(regionDir, type.cdnType), "$regionCode.json")
    }

    private companion object {
        // 지자체가 뒤늦게 좌표를 채워 넣는 일이 있어 분기마다 한 번은 다시 받는다.
        const val DEFAULT_TTL_DAYS: Long = 90
    }
}
