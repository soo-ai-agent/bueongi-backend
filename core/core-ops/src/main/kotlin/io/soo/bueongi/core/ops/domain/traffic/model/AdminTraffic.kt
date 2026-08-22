package io.soo.bueongi.core.ops.domain.traffic.model

import io.soo.bueongi.support.util.HourlyBucket

/**
 * 관리자 화면이 부른 요청. 사용자 트래픽과 **같은 표에 섞지 않는다** —
 * 미리보기가 5초마다 폴링해서 섞으면 사용자 수·호출량이 부풀어 보인다.
 * 사용자 수 추정은 두지 않는다(관리자는 한 사람이다).
 */
data class AdminTraffic(
    val totalCalls: Int,
    val callsToday: Int,
    val windowSize: Int,
    /** 보관 상한 — 여기 닿았으면 오래된 기록이 밀려나 실제 누적은 더 많다. */
    val windowCap: Int,
    val byEndpoint: List<EndpointTraffic>,
    val hourlyCalls: List<HourlyBucket>,
)
