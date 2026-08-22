package io.soo.bueongi.client.tmap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TmapRouteCacheTest {

    private val origin = TmapCoord(37.5, 127.0)
    private val dest = TmapCoord(37.51, 127.01)
    private val route = TmapRoute("0", listOf(origin, dest), 880.0, 700, emptyList())

    @Test
    fun `동일 출발-도착-옵션 재요청은 캐시 히트`() {
        val cache = TmapRouteCache(clock = { 0L })
        assertThat(cache.get(origin, dest, "0", null)).isNull()
        cache.put(origin, dest, "0", null, route)
        assertThat(cache.get(origin, dest, "0", null)).isEqualTo(route)
    }

    @Test
    fun `TTL 지나면 만료되어 miss`() {
        var now: Long = 0L
        val cache = TmapRouteCache(ttlMillis = 1000, clock = { now })
        cache.put(origin, dest, "0", null, route)
        now = 999
        assertThat(cache.get(origin, dest, "0", null)).isEqualTo(route)
        now = 1000
        assertThat(cache.get(origin, dest, "0", null)).isNull()
    }

    /**
     * 소수 5자리(약 1.1m)로 잡았더니 **제자리에 서 있어도** GPS 가 그만큼 흔들려 재요청이
     * 매번 캐시를 빗나갔다(실측: 출발지 3.3m 이동에 경로 호출 7회 전량 재발생).
     */
    @Test
    fun `서 있을 때의 GPS 흔들림은 같은 격자로 본다`() {
        val cache = TmapRouteCache(clock = { 0L })
        cache.put(origin, dest, "0", null, route)

        val jitter = TmapCoord(37.500001, 127.000004)
        assertThat(cache.get(jitter, dest, "0", null)).isEqualTo(route)
        // 약 3.3m — 옛 격자(1.1m)에서는 빗나가던 거리다.
        val threeMeters = TmapCoord(37.50003, 127.00003)
        assertThat(cache.get(threeMeters, dest, "0", null)).isEqualTo(route)
    }

    /** 격자를 더 키우면 안내 시작점이 눈에 띄게 어긋난다 — 걸어서 벗어난 거리는 갈라야 한다. */
    @Test
    fun `격자를 벗어나면 다른 요청으로 본다`() {
        val cache = TmapRouteCache(clock = { 0L })
        cache.put(origin, dest, "0", null, route)

        // 약 33m — 이 거리의 경로를 같은 것으로 주면 시작점이 어긋난다.
        val farOrigin = TmapCoord(37.5003, 127.0)
        assertThat(cache.get(farOrigin, dest, "0", null)).isNull()
        val farDest = TmapCoord(37.5103, 127.01)
        assertThat(cache.get(origin, farDest, "0", null)).isNull()
    }

    @Test
    fun `옵션-경유지가 다르면 키 분리`() {
        val cache = TmapRouteCache(clock = { 0L })
        cache.put(origin, dest, "0", null, route)
        assertThat(cache.get(origin, dest, "10", null)).isNull()
        assertThat(cache.get(origin, dest, "0", listOf(TmapCoord(37.505, 127.005)))).isNull()
    }

    @Test
    fun `용량 상한 초과 시 비워지고 이후 정상 동작`() {
        val cache = TmapRouteCache(maxEntries = 2, clock = { 0L })
        cache.put(origin, dest, "0", null, route)
        cache.put(origin, dest, "4", null, route)
        assertThat(cache.size()).isEqualTo(2)
        cache.put(origin, dest, "10", null, route)
        assertThat(cache.size()).isEqualTo(1)
        assertThat(cache.get(origin, dest, "10", null)).isEqualTo(route)
    }
}
