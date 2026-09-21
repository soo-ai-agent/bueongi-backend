package io.soo.bueongi.core.route.stats.domain.model

/** 엔드포인트별 건수. 수신 요청 지표(ops)와 경로 추천 통계(route)가 같은 모양을 쓴다. */
data class EndpointCount(
    val endpoint: String,
    val count: Int,
)
