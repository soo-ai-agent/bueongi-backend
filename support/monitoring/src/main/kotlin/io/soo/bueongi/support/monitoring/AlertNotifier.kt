package io.soo.bueongi.support.monitoring

interface AlertNotifier {
    val isEnabled: Boolean

    /** 계약: 절대 예외를 던지지 않고 호출 스레드를 막지 않는다(비동기) — key 는 스로틀 단위(창 내 1회 발송). */
    fun notify(key: String, title: String, detail: String)

    fun findRecentAlerts(limit: Int): List<AlertRecord> = emptyList()

    /** 계약: 동기 발송(스로틀·뮤트 무시), 비활성·실패는 false — 절대 예외를 던지지 않는다. */
    fun sendTest(): Boolean = false
}

const val ALERT_HISTORY_MAX_LIMIT: Int = 500
