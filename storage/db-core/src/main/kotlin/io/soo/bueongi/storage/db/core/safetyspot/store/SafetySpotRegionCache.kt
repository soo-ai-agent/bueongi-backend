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
    @param:Value("\${bueongi.facility.region-cache-ttl-days:90}")
    private val ttlDays: Long = DEFAULT_TTL_DAYS,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotRegionCache::class.java)

    private val writeMapper: ObjectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    private val readMapper: ObjectMapper = objectMapper

    /**
     * 파일에서 한 번 읽은 저장 시각을 기억한다.
     * 이 값은 우리가 [save] 할 때만 바뀌므로, 매번 파일을 다시 파싱할 이유가 없다 —
     * 지연 적재는 요청마다 만료를 확인하는데 파일이 수 MB 라 그 비용이 그대로 요청에 실렸다.
     */
    private val savedAtByFile: ConcurrentHashMap<String, Instant> = ConcurrentHashMap()

    /**
     * 이미 받아 뒀고 아직 안 낡았는지. 낡았으면 false 라 다시 받는다.
     *
     * 만료를 두는 이유는 지자체가 나중에 좌표를 채워 넣어도 우리가 모르기 때문이다 —
     * 좌표 0% 라 빈 파일로 남긴 지역이 특히 그렇다.
     */
    fun isCached(type: SafetySpotType, regionCode: String): Boolean {
        val savedAt: Instant = savedAtOf(type, regionCode) ?: return false
        return Duration.between(savedAt, clock.instant()) < Duration.ofDays(ttlDays)
    }

    /** 저장 시각. 파일이 없거나 시각을 못 읽으면 null — 그때는 안 받은 것으로 본다. */
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

    private fun cacheKey(type: SafetySpotType, regionCode: String): String =
        "${type.cdnType}/$regionCode"

    private fun fileOf(type: SafetySpotType, regionCode: String): File =
        File(File(regionDir, type.cdnType), "$regionCode.json")

    private companion object {
        /** 보안등 원천은 갱신이 드물지만, 지자체가 좌표를 채워 넣는 일이 있어 분기마다 한 번은 다시 본다. */
        const val DEFAULT_TTL_DAYS: Long = 90
    }
}
