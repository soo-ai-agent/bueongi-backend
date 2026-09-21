package io.soo.bueongi.storage.db.core.adevent

interface AdEventStore {
    // 계약: 절대 예외를 던지지 않는다 — 계측 실패가 실제 요청을 깨면 안 된다.
    fun record(event: AdEvent)

    fun findRecent(limit: Int): List<AdEvent>
}

const val AD_EVENT_CAP: Int = 5000
