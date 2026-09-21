package io.soo.bueongi.storage.db.core.safetyspot

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SafetySpotRegionCacheTest {

    @Test
    fun `저장한 지역을 다시 읽어 온다`(@TempDir tmp: File) {
        val cache = cacheOf(tmp)
        cache.save(SafetySpotType.STREET_LAMP, "3210000", listOf(lamp("서초1", 37.48, 127.01)))

        assertThat(cache.loadAll(SafetySpotType.STREET_LAMP)).hasSize(1)
        assertThat(cache.loadAll(SafetySpotType.STREET_LAMP)[0].name).isEqualTo("서초1")
    }

    @Test
    fun `한 지역을 받아 뒀다고 다른 지역까지 받은 것이 되지 않는다`(@TempDir tmp: File) {
        val cache = cacheOf(tmp)
        cache.save(SafetySpotType.STREET_LAMP, "3210000", listOf(lamp("서초1", 37.48, 127.01)))

        assertThat(cache.isCached(SafetySpotType.STREET_LAMP, "3210000")).isTrue()
        assertThat(cache.isCached(SafetySpotType.STREET_LAMP, "3860000")).isFalse()
        assertThat(cache.isCached(SafetySpotType.SECURITY_LAMP, "3210000")).isFalse()
    }

    @Test
    fun `지역마다 파일이 나뉘어 서로 덮어쓰지 않는다`(@TempDir tmp: File) {
        val cache = cacheOf(tmp)
        cache.save(SafetySpotType.STREET_LAMP, "3210000", listOf(lamp("서초1", 37.48, 127.01)))
        cache.save(SafetySpotType.STREET_LAMP, "3200000", listOf(lamp("관악1", 37.47, 126.95)))

        assertThat(cache.loadAll(SafetySpotType.STREET_LAMP)).hasSize(2)
        assertThat(cache.cachedRegions(SafetySpotType.STREET_LAMP)).containsExactlyInAnyOrder("3210000", "3200000")
    }

    @Test
    fun `저장한 지역은 이미 받은 것으로 본다`(@TempDir tmp: File) {
        val cache = cacheOf(tmp)

        assertThat(cache.isCached(SafetySpotType.STREET_LAMP, "3210000")).isFalse()
        cache.save(SafetySpotType.STREET_LAMP, "3210000", emptyList())
        assertThat(cache.isCached(SafetySpotType.STREET_LAMP, "3210000")).isTrue()
    }

    private fun cacheOf(tmp: File, clock: Clock = Clock.systemUTC()): SafetySpotRegionCache =
        SafetySpotRegionCache(ObjectMapper(), File(tmp, "region").absolutePath, TTL_DAYS, clock)

    @Test
    fun `저장 시각을 함께 남긴다`(@TempDir tmp: File) {
        val saved = Instant.parse("2026-08-17T00:00:00Z")
        val cache = cacheOf(tmp, Clock.fixed(saved, ZoneOffset.UTC))

        cache.save(SafetySpotType.STREET_LAMP, "3210000", listOf(lamp("서초1", 37.48, 127.01)))

        assertThat(cache.savedAtOf(SafetySpotType.STREET_LAMP, "3210000")).isEqualTo(saved)
    }

    @Test
    fun `만료 기간이 지나면 다시 받아야 하는 것으로 본다`(@TempDir tmp: File) {
        val saved = Instant.parse("2026-08-17T00:00:00Z")
        cacheOf(tmp, Clock.fixed(saved, ZoneOffset.UTC))
            .save(SafetySpotType.STREET_LAMP, "3210000", listOf(lamp("서초1", 37.48, 127.01)))

        val justBefore = Clock.fixed(saved.plus(Duration.ofDays(TTL_DAYS - 1)), ZoneOffset.UTC)
        val justAfter = Clock.fixed(saved.plus(Duration.ofDays(TTL_DAYS + 1)), ZoneOffset.UTC)

        assertThat(cacheOf(tmp, justBefore).isCached(SafetySpotType.STREET_LAMP, "3210000")).isTrue()
        assertThat(cacheOf(tmp, justAfter).isCached(SafetySpotType.STREET_LAMP, "3210000")).isFalse()
    }

    @Test
    fun `만료돼도 저장된 데이터는 계속 읽힌다`(@TempDir tmp: File) {
        val saved = Instant.parse("2026-08-17T00:00:00Z")
        cacheOf(tmp, Clock.fixed(saved, ZoneOffset.UTC))
            .save(SafetySpotType.STREET_LAMP, "3210000", listOf(lamp("서초1", 37.48, 127.01)))
        val expired = cacheOf(tmp, Clock.fixed(saved.plus(Duration.ofDays(TTL_DAYS + 1)), ZoneOffset.UTC))

        assertThat(expired.loadAll(SafetySpotType.STREET_LAMP)).hasSize(1)
    }

    private fun lamp(name: String, lat: Double, lng: Double): SafetySpotPoint =
        SafetySpotPoint(type = SafetySpotType.STREET_LAMP, lat = lat, lng = lng, name = name)

    private companion object {
        const val TTL_DAYS: Long = 90
    }
}
