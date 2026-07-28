package io.soo.bueongi.client.tmap

import java.util.concurrent.ConcurrentHashMap

/**
 * Tmap 보행자 경로 응답 캐시(P0-2 쿼터 거버넌스).
 *
 * 같은 출발/도착/옵션/경유지 조합은 짧은 시간 안에 재요청되는 일이 잦다(경로 비교 재진입,
 * 안심 강도 변경, 화면 왕복). 동일 키 재요청을 TTL 동안 재사용해 Tmap 일 쿼터/요금을 아낀다.
 *
 * - 키: 좌표를 소수 5자리(~1.1m)로 반올림해 GPS 미세 흔들림에도 캐시가 맞도록 정규화.
 * - TTL: 기본 10분 — 보행 경로는 이 안에서 사실상 불변. 만료 항목은 조회 시 게으르게 제거.
 * - 용량: [maxEntries] 초과 시 전체 비움(단순·예측 가능 — 트래픽 규모상 충분).
 * - null(호출 실패)은 캐시하지 않는다 — 일시 장애가 TTL 동안 고착되는 것을 막는다.
 *
 * clock 주입으로 시계 없이 단위 검증한다. thread-safe(ConcurrentHashMap).
 */
class TmapRouteCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val route: TmapRoute, val expiresAt: Long)

    private val entries: ConcurrentHashMap<String, Entry> = ConcurrentHashMap<String, Entry>()

    /** 캐시 조회. 없거나 만료면 null(만료 항목은 제거). */
    fun get(origin: TmapCoord, dest: TmapCoord, searchOption: String, passList: List<TmapCoord>?): TmapRoute? {
        val key: String = key(origin, dest, searchOption, passList)
        val entry: Entry = entries[key] ?: return null
        if (clock() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry.route
    }

    /** 성공 응답만 저장(null 은 호출부에서 걸러진다). 용량 초과 시 전체 비움. */
    fun put(origin: TmapCoord, dest: TmapCoord, searchOption: String, passList: List<TmapCoord>?, route: TmapRoute) {
        if (entries.size >= maxEntries) {
            entries.clear()
        }
        entries[key(origin, dest, searchOption, passList)] = Entry(route, clock() + ttlMillis)
    }

    /** 현재 항목 수(테스트/관측용). */
    fun size(): Int = entries.size

    private fun key(origin: TmapCoord, dest: TmapCoord, searchOption: String, passList: List<TmapCoord>?): String {
        val sb: StringBuilder = StringBuilder()
            .append(round5(origin.lat)).append(',').append(round5(origin.lng)).append('>')
            .append(round5(dest.lat)).append(',').append(round5(dest.lng)).append('#')
            .append(searchOption)
        passList?.forEach { sb.append('|').append(round5(it.lat)).append(',').append(round5(it.lng)) }
        return sb.toString()
    }

    /** 소수 5자리(~1.1m) 반올림 — GPS 미세 차이로 캐시가 어긋나지 않게. */
    private fun round5(v: Double): Double = Math.round(v * 1e5) / 1e5

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 10 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES: Int = 256
    }
}
