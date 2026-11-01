package io.soo.bueongi.client.tmap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TmapRouteCacheTest {
    private val origin = TmapCoord(37.5, 127.0)
    private val destination = TmapCoord(37.51, 127.01)
    private val route = TmapRoute("0", listOf(origin, destination), 880.0, 700, emptyList())

    @Test
    fun `동일 출발-도착-옵션 재요청은 캐시 히트`() {
        val cache = TmapRouteCache(clock = { 0L })
        assertThat(cache.get(origin, destination, "0", emptyList())).isNull()
        cache.put(origin, destination, "0", emptyList(), route)
        assertThat(cache.get(origin, destination, "0", emptyList())).isEqualTo(route)
    }

    @Test
    fun `TTL 지나면 만료되어 miss`() {
        var now: Long = 0L
        val cache = TmapRouteCache(ttlMillis = 1000, clock = { now })
        cache.put(origin, destination, "0", emptyList(), route)
        now = 999
        assertThat(cache.get(origin, destination, "0", emptyList())).isEqualTo(route)
        now = 1000
        assertThat(cache.get(origin, destination, "0", emptyList())).isNull()
    }

    @Test
    fun `서 있을 때의 GPS 흔들림은 같은 격자로 본다`() {
        val cache = TmapRouteCache(clock = { 0L })
        cache.put(origin, destination, "0", emptyList(), route)

        val jitter = TmapCoord(37.500001, 127.000004)
        assertThat(cache.get(jitter, destination, "0", emptyList())).isEqualTo(route)
        // 약 3.3m — 옛 격자(1.1m)에서는 빗나가던 거리다.
        val threeMeters = TmapCoord(37.50003, 127.00003)
        assertThat(cache.get(threeMeters, destination, "0", emptyList())).isEqualTo(route)
    }

    @Test
    fun `격자를 벗어나면 다른 요청으로 본다`() {
        val cache = TmapRouteCache(clock = { 0L })
        cache.put(origin, destination, "0", emptyList(), route)

        // 약 33m — 이 거리의 경로를 같은 것으로 주면 시작점이 어긋난다.
        val farOrigin = TmapCoord(37.5003, 127.0)
        assertThat(cache.get(farOrigin, destination, "0", emptyList())).isNull()
        val farDest = TmapCoord(37.5103, 127.01)
        assertThat(cache.get(origin, farDest, "0", emptyList())).isNull()
    }

    @Test
    fun `옵션-경유지가 다르면 키 분리`() {
        val cache = TmapRouteCache(clock = { 0L })
        cache.put(origin, destination, "0", emptyList(), route)
        assertThat(cache.get(origin, destination, "10", emptyList())).isNull()
        assertThat(cache.get(origin, destination, "0", listOf(TmapCoord(37.505, 127.005)))).isNull()
    }

    @Test
    fun `용량 상한 초과 시 비워지고 이후 정상 동작`() {
        val cache = TmapRouteCache(maxEntries = 2, clock = { 0L })
        cache.put(origin, destination, "0", emptyList(), route)
        cache.put(origin, destination, "4", emptyList(), route)
        assertThat(cache.get(origin, destination, "0", emptyList())).isEqualTo(route)
        assertThat(cache.get(origin, destination, "4", emptyList())).isEqualTo(route)
        cache.put(origin, destination, "10", emptyList(), route)
        assertThat(cache.get(origin, destination, "0", emptyList())).isNull()
        assertThat(cache.get(origin, destination, "4", emptyList())).isNull()
        assertThat(cache.get(origin, destination, "10", emptyList())).isEqualTo(route)
    }
}
