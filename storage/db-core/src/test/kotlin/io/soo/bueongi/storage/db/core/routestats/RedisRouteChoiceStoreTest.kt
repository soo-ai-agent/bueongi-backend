package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.StringRedisTemplate

class RedisRouteChoiceStoreTest {

    private val listOps: ListOperations<String, String> = mock()

    private val redis: StringRedisTemplate = mock<StringRedisTemplate>().also {
        whenever(it.opsForList()).thenReturn(listOps)
    }

    private val log = RouteChoiceLog(
        atEpochMs = 1L,
        chosenType = RouteType.SAFE,
        candidateCount = 3,
        score = 82,
        distanceMeters = 1200,
        durationSeconds = 900,
        preference = SafetyPreference.SAFE,
        destinationName = "강남역",
    )

    @Test
    fun `선택 기록은 routechoices 키에 상한만큼 쌓는다`() {
        RedisRouteChoiceStore(redis).record(log)

        verify(listOps).leftPush(eq(ROUTE_CHOICE_REDIS_KEY), any())
        verify(listOps).trim(ROUTE_CHOICE_REDIS_KEY, 0, (ROUTE_CHOICE_CAP - 1).toLong())
    }

    @Test
    fun `선택 기록은 추천 창을 건드리지 않는다`() {
        RedisRouteChoiceStore(redis).record(log)

        verify(listOps, never()).leftPush(eq("routestats"), any())
        verify(listOps, never()).trim(eq("routestats"), any(), any())
    }
}
