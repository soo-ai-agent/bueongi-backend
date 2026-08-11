package io.soo.bueongi.domain.facility

/**
 * 안심시설 라이브 갱신 호출 정책 — 외부 API 를 반복 호출하지 않기 위한 기준.
 * 갱신 1회가 수백~수천 회 호출이라, 겹쳐 돌거나 짧은 간격으로 다시 돌면 쿼터가 바로 소진된다.
 */
object FacilityRefreshPolicy {

    /**
     * 직전 갱신 시작 후 이 시간이 지나기 전의 재요청은 건너뛴다.
     * 정규 주기는 월 1회(cron)이고, 기동·수동 트리거가 겹쳐 들어오는 경우를 막는 하한이다.
     */
    const val MIN_INTERVAL_MS: Long = 6 * 60 * 60 * 1000L
}
