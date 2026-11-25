package io.soo.bueongi.support.monitoring

interface AlertNotifier {
    val isEnabled: Boolean

    fun notify(key: String, title: String, detail: String)

    fun findRecent(limit: Int): List<AlertRecord> {
        return emptyList()
    }

    fun sendTest(): Boolean {
        return false
    }

    companion object {
        const val HISTORY_MAX_LIMIT: Int = 500
    }
}
