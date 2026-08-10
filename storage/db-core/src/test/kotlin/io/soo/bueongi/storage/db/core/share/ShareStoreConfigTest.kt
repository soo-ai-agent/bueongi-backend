package io.soo.bueongi.storage.db.core.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.mock.env.MockEnvironment

class ShareStoreConfigTest {

    private val redisConfigured: RedisConfigured = RedisConfigured()
    private val redisNotConfigured: RedisNotConfigured = RedisNotConfigured()
    private val metadata = mock<AnnotatedTypeMetadata>()

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
