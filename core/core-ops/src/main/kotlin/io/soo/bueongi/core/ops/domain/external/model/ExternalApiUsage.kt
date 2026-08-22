package io.soo.bueongi.core.ops.domain.external.model

import io.soo.bueongi.core.enums.ExternalApiProvider
import io.soo.bueongi.support.util.HourlyBucket

/** 원천 하나의 호출 현황. */
data class ExternalApiUsage(
    val provider: ExternalApiProvider,
    val totalCalls: Int,
    val failureCount: Int,
    val successRate: Double,
    val avgDurationMs: Long,
    val p95DurationMs: Long,
    /** 오늘(서비스 시간대 기준) 호출 수 — 일일 한도가 있는 원천을 볼 때 쓴다. */
    val callsToday: Int,
    val callsLastHour: Int,
    // 기록이 남아 있는 동안 한 번도 안 불렀으면 null 이 가능함
    val lastCallAtEpochMs: Long?,
    // 실패가 없었으면 null 이 가능함
    val lastFailureReason: String?,
    val hourlyCalls: List<HourlyBucket>,
    val topEndpoints: List<ExternalApiEndpoint>,
)
