package io.soo.bueongi.storage.db.core.requestmetric


interface RequestMetricStore {
    /** 계약: 절대 예외를 던지지 않는다(계측 실패가 실제 요청을 깨면 안 됨 — 내부에서 삼킨다). */
    fun record(metric: RequestMetric)

    fun findRecent(limit: Int): List<RequestMetric>
}

const val REQUEST_METRIC_CAP: Int = 5000
