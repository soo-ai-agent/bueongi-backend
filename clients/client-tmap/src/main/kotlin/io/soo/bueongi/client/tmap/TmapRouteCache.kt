package io.soo.bueongi.client.tmap

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class TmapRouteCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val route: TmapRoute, val expiresAt: Long)

    private val entries: ConcurrentHashMap<String, Entry> = ConcurrentHashMap<String, Entry>()

    fun get(origin: TmapCoord, dest: TmapCoord, searchOption: String, passList: List<TmapCoord>?): TmapRoute? {
        val key: String = key(origin, dest, searchOption, passList)
        val entry: Entry = entries[key] ?: return null
        if (clock() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry.route
    }

    fun put(origin: TmapCoord, dest: TmapCoord, searchOption: String, passList: List<TmapCoord>?, route: TmapRoute) {
        if (entries.size >= maxEntries) {
            entries.clear()
        }
        entries[key(origin, dest, searchOption, passList)] = Entry(route, clock() + ttlMillis)
    }

    fun size(): Int =
        entries.size

    private fun key(origin: TmapCoord, dest: TmapCoord, searchOption: String, passList: List<TmapCoord>?): String {
        val sb = StringBuilder()
            .append(snap(origin.lat)).append(',').append(snap(origin.lng)).append('>')
            .append(snap(dest.lat)).append(',').append(snap(dest.lng)).append('#')
            .append(searchOption)
        passList?.forEach { sb.append('|').append(snap(it.lat)).append(',').append(snap(it.lng)) }
        return sb.toString()
    }

    /**
     * 좌표를 약 11m 격자에 맞춘다. 소수 5자리(약 1.1m)로 잡으면 **제자리에 서 있어도**
     * GPS 가 그만큼 흔들려 같은 자리의 재요청이 매번 캐시를 빗나갔다(실측: 출발지 3.3m 이동에
     * 경로 호출 7회 전량 재발생).
     *
     * 걸어서 움직이는 중에는 이 격자로도 대개 빗나간다 — 이것이 고치는 것은 "같은 자리에서
     * 다시 부르는 경우"다. 격자를 더 키우면 안내 시작점이 눈에 띄게 어긋난다.
     */
    private fun snap(v: Double): Double =
        (v * GRID_PER_DEGREE).roundToInt() / GRID_PER_DEGREE

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 10 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES: Int = 256

        /** 소수 4자리 = 약 11m. 서 있을 때의 GPS 흔들림(대개 ±5m)을 덮는 가장 작은 격자다. */
        private const val GRID_PER_DEGREE: Double = 1e4
    }
}
