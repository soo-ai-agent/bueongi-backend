package io.soo.bueongi.storage.db.core

class InMemoryRecentLog<T : Any>(
    private val cap: Int,
) {
    private val items: ArrayDeque<T> = ArrayDeque<T>()
    private val lock = Any()

    fun push(item: T) {
        synchronized(lock) {
            items.addFirst(item)
            while (items.size > cap) {
                items.removeLast()
            }
        }
    }

    fun readRecent(limit: Int): List<T> {
        val snapshot: List<T> = synchronized(lock) { items.toList() }
        return snapshot.take(limit.coerceAtLeast(0))
    }
}
