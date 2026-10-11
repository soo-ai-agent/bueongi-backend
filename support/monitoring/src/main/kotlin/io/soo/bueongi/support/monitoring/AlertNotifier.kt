package io.soo.bueongi.support.monitoring

interface AlertNotifier {
    val isEnabled: Boolean

    // 구현은 예외를 던지지 않고 호출 스레드를 막지 않는다 — key 는 스로틀 단위(창 내 1회 발송).
    fun notify(key: String, title: String, detail: String)

    fun findRecentAlerts(limit: Int): List<AlertRecord> {
        return emptyList()
    }

    // 동기 발송(스로틀·뮤트 무시), 비활성·실패는 false — 예외를 던지지 않는다.
    fun sendTest(): Boolean {
        return false
    }
}

const val ALERT_HISTORY_MAX_LIMIT: Int = 500
