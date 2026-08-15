package io.soo.bueongi.storage.db.core

import io.soo.bueongi.storage.db.core.requestmetric.REQUEST_METRIC_CAP
import io.soo.bueongi.storage.db.core.requestmetric.RedisRequestMetricStore
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RedisRouteStatsStore
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.core.enums.SafetyPreference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.StringRedisTemplate

/** 두 저장소가 각자의 Redis 키와 상한을 쓰는지 고정한다 — 공용 [RedisJsonLog] 로 묶으며 섞이기 쉬운 지점. */
class RedisStoreWiringTest {

    private val listOps: ListOperations<String, String> = mock()

    private val redis: StringRedisTemplate = mock<StringRedisTemplate>().also {
        whenever(it.opsForList()).thenReturn(listOps)
    }

    @Test
    fun `추천 통계는 routestats 키에 상한만큼 쌓는다`() {
        RedisRouteStatsStore(redis).record(
            RouteRecommendationLog(
                atEpochMs = 1L,
                endpoint = "safe-compare",
                destinationName = "강남역",
                preference = SafetyPreference.SAFE,
            ),
        )

        verify(listOps).leftPush(eq("routestats"), any())
        verify(listOps).trim("routestats", 0, (ROUTE_STATS_CAP - 1).toLong())
    }

    @Test
    fun `요청 메트릭은 requestmetric 키에 상한만큼 쌓는다`() {
        RedisRequestMetricStore(redis).record(
            RequestMetric(
                atEpochMs = 1L,
                method = "POST",
                endpoint = "/api/routes/safe-compare",
                clientId = null,
                status = 200,
                durationMs = 30L,
            ),
        )

        verify(listOps).leftPush(eq("requestmetric"), any())
        verify(listOps).trim("requestmetric", 0, (REQUEST_METRIC_CAP - 1).toLong())
    }

}
