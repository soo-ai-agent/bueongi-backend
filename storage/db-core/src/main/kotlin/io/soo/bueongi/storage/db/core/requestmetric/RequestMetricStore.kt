package io.soo.bueongi.storage.db.core.requestmetric

interface RequestMetricStore {
    /** 계약: 절대 예외를 던지지 않는다(계측 실패가 실제 요청을 깨면 안 됨 — 내부에서 삼킨다). */
    fun record(metric: RequestMetric)

    fun findRecent(limit: Int): List<RequestMetric>
}

const val REQUEST_METRIC_CAP: Int = 5000

/**
 * 관리자 창은 따로 두고 더 얕게 잡는다 — 미리보기가 5초마다 폴링해서,
 * 한 창을 같이 쓰면 관리자 호출이 사용자 기록을 밀어낸다.
 */
const val ADMIN_REQUEST_METRIC_CAP: Int = 1000
