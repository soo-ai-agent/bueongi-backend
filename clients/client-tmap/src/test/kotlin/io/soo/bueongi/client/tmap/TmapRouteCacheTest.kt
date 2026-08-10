package io.soo.bueongi.client.tmap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TmapRouteCacheTest {

    private val origin: TmapCoord = TmapCoord(37.5, 127.0)
    private val dest: TmapCoord = TmapCoord(37.51, 127.01)
    private val route: TmapRoute = TmapRoute("0", listOf(origin, dest), 880.0, 700, emptyList())

    @Test
    fun `동일 출발-도착-옵션 재요청은 캐시 히트`() {
        val cache: TmapRouteCache = TmapRouteCache(clock = { 0L })
        assertThat(cache.get(origin, dest, "0", null)).isNull()
        cache.put(origin, dest, "0", null, route)
        assertThat(cache.get(origin, dest, "0", null)).isEqualTo(route)
    }

    @Test
    fun `TTL 지나면 만료되어 miss`() {
        var now: Long = 0L
        val cache: TmapRouteCache = TmapRouteCache(ttlMillis = 1000, clock = { now })
        cache.put(origin, dest, "0", null, route)
        now = 999
        assertThat(cache.get(origin, dest, "0", null)).isEqualTo(route)
        now = 1000
        assertThat(cache.get(origin, dest, "0", null)).isNull()
    }

    @Test
    fun `좌표 소수 5자리 반올림 - GPS 미세 흔들림에도 히트`() {
        val cache: TmapRouteCache = TmapRouteCache(clock = { 0L })
        cache.put(origin, dest, "0", null, route)
        val jitter: TmapCoord = TmapCoord(37.500001, 127.000004)
        assertThat(cache.get(jitter, dest, "0", null)).isEqualTo(route)
        val far: TmapCoord = TmapCoord(37.5001, 127.0)
        assertThat(cache.get(far, dest, "0", null)).isNull()
    }

    @Test
    fun `옵션-경유지가 다르면 키 분리`() {
        val cache: TmapRouteCache = TmapRouteCache(clock = { 0L })
        cache.put(origin, dest, "0", null, route)
        assertThat(cache.get(origin, dest, "10", null)).isNull()
        assertThat(cache.get(origin, dest, "0", listOf(TmapCoord(37.505, 127.005)))).isNull()
    }

    @Test
    fun `용량 상한 초과 시 비워지고 이후 정상 동작`() {
        val cache: TmapRouteCache = TmapRouteCache(maxEntries = 2, clock = { 0L })
        cache.put(origin, dest, "0", null, route)
        cache.put(origin, dest, "4", null, route)
        assertThat(cache.size()).isEqualTo(2)
        cache.put(origin, dest, "10", null, route)
        assertThat(cache.size()).isEqualTo(1)
        assertThat(cache.get(origin, dest, "10", null)).isEqualTo(route)
    }
}
