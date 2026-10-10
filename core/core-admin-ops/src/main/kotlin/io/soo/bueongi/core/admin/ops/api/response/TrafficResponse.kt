package io.soo.bueongi.core.admin.ops.api.response

import io.soo.bueongi.core.admin.ops.domain.traffic.model.AdminTraffic
import io.soo.bueongi.core.admin.ops.domain.traffic.model.DailyActive
import io.soo.bueongi.core.admin.ops.domain.traffic.model.EndpointTraffic
import io.soo.bueongi.core.admin.ops.domain.traffic.model.EstimatedUsers
import io.soo.bueongi.core.admin.ops.domain.traffic.model.LatencyPercentiles
import io.soo.bueongi.core.admin.ops.domain.traffic.model.TrafficSummary
import io.soo.bueongi.support.util.HourlyBucket

data class TrafficResponse(
    val totalCalls: Int,
    val successCount: Int,
    val failureCount: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointTraffic>,
    val estimatedUsers: EstimatedUsers,
    val latency: LatencyPercentiles,
    val hourlyCalls: List<HourlyBucket>,
    val hourlyErrors: List<HourlyBucket>,
    val dailyActiveUsers: List<DailyActive>,
    val adminCalls: AdminTraffic,
) {
    companion object {
        fun from(summary: TrafficSummary): TrafficResponse {
            return TrafficResponse(
                totalCalls = summary.totalCalls,
                successCount = summary.successCount,
                failureCount = summary.failureCount,
                windowSize = summary.windowSize,
                byEndpoint = summary.byEndpoint,
                estimatedUsers = summary.estimatedUsers,
                latency = summary.latency,
                hourlyCalls = summary.hourlyCalls,
                hourlyErrors = summary.hourlyErrors,
                dailyActiveUsers = summary.dailyActiveUsers,
                adminCalls = summary.adminCalls,
            )
        }
    }
}
