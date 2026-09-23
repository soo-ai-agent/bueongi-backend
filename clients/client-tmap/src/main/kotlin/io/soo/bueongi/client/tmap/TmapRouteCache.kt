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

    fun get(
        origin: TmapCoord,
        destination: TmapCoord,
        searchOption: String,
        passList: List<TmapCoord>,
    ): TmapRoute? {
        val key: String = key(origin, destination, searchOption, passList)
        val entry: Entry = entries[key] ?: return null
        if (clock() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry.route
    }

    fun put(
        origin: TmapCoord,
        destination: TmapCoord,
        searchOption: String,
        passList: List<TmapCoord>,
        route: TmapRoute,
    ) {
        if (entries.size >= maxEntries) {
            entries.clear()
        }
        entries[key(origin, destination, searchOption, passList)] = Entry(route, clock() + ttlMillis)
    }

    fun size(): Int = entries.size

    private fun key(
        origin: TmapCoord,
        destination: TmapCoord,
        searchOption: String,
        passList: List<TmapCoord>,
    ): String {
        val sb =
            StringBuilder()
                .append(snap(origin.lat)).append(',').append(snap(origin.lng)).append('>')
                .append(snap(destination.lat)).append(',').append(snap(destination.lng)).append('#')
                .append(searchOption)
        for (waypoint in passList) {
            sb.append('|').append(snap(waypoint.lat)).append(',').append(snap(waypoint.lng))
        }
        return sb.toString()
    }

    private fun snap(v: Double): Double = (v * GRID_PER_DEGREE).roundToInt() / GRID_PER_DEGREE

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 10 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES: Int = 256

        // 소수 4자리 = 약 11m — 서 있을 때의 GPS 흔들림(±5m)을 덮는 가장 작은 격자다.
        private const val GRID_PER_DEGREE: Double = 1e4
    }
}
