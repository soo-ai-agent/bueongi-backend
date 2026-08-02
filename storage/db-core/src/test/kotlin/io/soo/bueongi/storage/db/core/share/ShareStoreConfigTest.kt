package io.soo.bueongi.storage.db.core.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.mock.env.MockEnvironment

/**
 * [ShareStoreConfig] 의 저장소 배선 분기([RedisConfigured]/[RedisNotConfigured]) 회귀 가드.
 *
 * 이 두 [org.springframework.context.annotation.Condition] 은 **빈 env 함정** 회피가 존재 이유다 —
 * `@ConditionalOnProperty` 는 빈 문자열(`redis-url: ${BUEONGI_REDIS_URL:}`)을 "있음"으로 보는 경우가 있어
 * 미설정 환경에서도 Redis 모드로 잘못 매칭될 수 있다. 그래서 명시적 Condition 으로 분기한다(`ShareStoreConfig` 주석).
 *
 * 이 동작을 잠그는 테스트가 없으면 다음 회귀가 142개 그린을 통과한 채 조용히 깨진다:
 *  1. **빈 env 함정**: 미설정/빈/공백 redis-url 인데 Redis 모드로 매칭 → 부팅 시 존재하지 않는 Redis 접속 시도/실패.
 *  2. **여집합 깨짐**: 두 Condition 이 정확한 여집합이 아니면 `locationStore` 빈이 0개(기동 실패) 또는 2개(빈 충돌)가 된다.
 *     production 은 정확히 한쪽만 빈을 만들어야 한다(어떤 redis-url 값에서도 `locationStore` 정확히 1개).
 */
class ShareStoreConfigTest {

    private val redisConfigured: RedisConfigured = RedisConfigured()
    private val redisNotConfigured: RedisNotConfigured = RedisNotConfigured()
    private val metadata = mock<AnnotatedTypeMetadata>()

    /** production 과 동일하게 default "" 로 조회하는 [MockEnvironment] 를 단 [ConditionContext]. */
    private fun contextWith(redisUrl: String?): ConditionContext {
        val env: MockEnvironment = MockEnvironment()
        if (redisUrl != null) {
            env.setProperty("bueongi.share.redis-url", redisUrl)
        }
        return mock { on { environment } doReturn env }
    }

    @Test
    fun `redis-url 이 채워지면 Redis 모드만 매칭`() {
        val ctx = contextWith("redis://localhost:6379")
        assertTrue(redisConfigured.matches(ctx, metadata))
        assertFalse(redisNotConfigured.matches(ctx, metadata))
    }

    @Test
    fun `빈 redis-url 은 인메모리 폴백만 매칭(빈 env 함정 회피)`() {
        val ctx = contextWith("")
        assertFalse(redisConfigured.matches(ctx, metadata))
        assertTrue(redisNotConfigured.matches(ctx, metadata))
    }

    @Test
    fun `공백뿐인 redis-url 도 인메모리 폴백만 매칭`() {
        val ctx = contextWith("   ")
        assertFalse(redisConfigured.matches(ctx, metadata))
        assertTrue(redisNotConfigured.matches(ctx, metadata))
    }

    @Test
    fun `redis-url 미설정(키 부재)이면 인메모리 폴백만 매칭`() {
        val ctx = contextWith(null)
        assertFalse(redisConfigured.matches(ctx, metadata))
        assertTrue(redisNotConfigured.matches(ctx, metadata))
    }

    @Test
    fun `두 Condition 은 모든 입력에서 정확한 여집합(locationStore 빈 항상 1개)`() {
        listOf(null, "", "   ", "redis://localhost:6379", "  redis://host:6379  ").forEach { url ->
            val ctx = contextWith(url)
            assertTrue(
                redisConfigured.matches(ctx, metadata) != redisNotConfigured.matches(ctx, metadata),
                "redis-url=<$url> 에서 두 Condition 이 여집합이 아님 — locationStore 빈이 0개 또는 2개가 된다",
            )
        }
    }
}
