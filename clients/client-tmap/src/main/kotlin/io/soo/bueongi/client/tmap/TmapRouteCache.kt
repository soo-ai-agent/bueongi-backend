package io.soo.bueongi.client.tmap

import java.util.concurrent.ConcurrentHashMap

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
        val sb: StringBuilder = StringBuilder()
            .append(round5(origin.lat)).append(',').append(round5(origin.lng)).append('>')
            .append(round5(dest.lat)).append(',').append(round5(dest.lng)).append('#')
            .append(searchOption)
        passList?.forEach { sb.append('|').append(round5(it.lat)).append(',').append(round5(it.lng)) }
        return sb.toString()
    }

    private fun round5(v: Double): Double =
        Math.round(v * 1e5) / 1e5

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 10 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES: Int = 256
    }
}
