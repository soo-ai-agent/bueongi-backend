package io.soo.bueongi.core.route.domain.safety.service

import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCluster

// 경로 직선 위에 투영해 본 덩어리 — 어디쯤(progress) 있는지를 같이 든다.
internal data class PlacedCluster(
    val cluster: SafetySpotCluster,
    val progress: Double,
)
