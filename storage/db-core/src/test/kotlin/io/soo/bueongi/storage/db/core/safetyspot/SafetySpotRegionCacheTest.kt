package io.soo.bueongi.storage.db.core.safetyspot

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SafetySpotRegionCacheTest {

    @Test
    fun `저장한 지역을 다시 읽어 온다`(@TempDir tmp: File) {
        val cache = cacheOf(tmp)
        cache.save(SafetySpotType.LAMP, "3210000", listOf(lamp("서초1", 37.48, 127.01)))

        assertThat(cache.loadAll(SafetySpotType.LAMP)).hasSize(1)
        assertThat(cache.loadAll(SafetySpotType.LAMP)[0].name).isEqualTo("서초1")
    }

    @Test
    fun `지역마다 파일이 나뉘어 서로 덮어쓰지 않는다`(@TempDir tmp: File) {
        val cache = cacheOf(tmp)
        cache.save(SafetySpotType.LAMP, "3210000", listOf(lamp("서초1", 37.48, 127.01)))
        cache.save(SafetySpotType.LAMP, "3200000", listOf(lamp("관악1", 37.47, 126.95)))

        assertThat(cache.loadAll(SafetySpotType.LAMP)).hasSize(2)
        assertThat(cache.cachedRegions(SafetySpotType.LAMP)).containsExactlyInAnyOrder("3210000", "3200000")
    }

    @Test
    fun `저장한 지역은 이미 받은 것으로 본다`(@TempDir tmp: File) {
        val cache = cacheOf(tmp)

        assertThat(cache.isCached(SafetySpotType.LAMP, "3210000")).isFalse()
        cache.save(SafetySpotType.LAMP, "3210000", emptyList())
        assertThat(cache.isCached(SafetySpotType.LAMP, "3210000")).isTrue()
    }

    private fun cacheOf(tmp: File): SafetySpotRegionCache =
        SafetySpotRegionCache(ObjectMapper(), File(tmp, "region").absolutePath)

    private fun lamp(name: String, lat: Double, lng: Double): SafetySpotPoint =
        SafetySpotPoint(type = SafetySpotType.LAMP, lat = lat, lng = lng, name = name)
}
