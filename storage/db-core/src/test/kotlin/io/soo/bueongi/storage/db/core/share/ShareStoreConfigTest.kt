package io.soo.bueongi.storage.db.core.share

import io.soo.bueongi.storage.db.core.share.store.InMemoryLocationStore
import io.soo.bueongi.storage.db.core.share.store.LocationStore
import io.soo.bueongi.storage.db.core.share.store.RedisConfigured
import io.soo.bueongi.storage.db.core.share.store.RedisLocationStore
import io.soo.bueongi.storage.db.core.share.store.ShareStoreConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mock.env.MockEnvironment
import java.time.Clock

class ShareStoreConfigTest {
    private val redisConfigured = RedisConfigured()
    private val metadata = mock<AnnotatedTypeMetadata>()

    private fun contextWith(redisUrl: String?): ConditionContext {
        val env = MockEnvironment()
        if (redisUrl != null) {
            env.setProperty("bueongi.share.redis-url", redisUrl)
        }
        return mock { on { environment } doReturn env }
    }

    @Test
    fun `redis-url 이 채워지면 Redis 모드를 켠다`() {
        assertTrue(redisConfigured.matches(contextWith("redis://localhost:6379"), metadata))
    }

    @Test
    fun `빈 redis-url 이면 Redis 모드를 켜지 않는다(빈 env 함정 회피)`() {
        assertFalse(redisConfigured.matches(contextWith(""), metadata))
    }

    @Test
    fun `공백뿐인 redis-url 이면 Redis 모드를 켜지 않는다`() {
        assertFalse(redisConfigured.matches(contextWith("   "), metadata))
    }

    @Test
    fun `redis-url 미설정(키 부재)이면 Redis 모드를 켜지 않는다`() {
        assertFalse(redisConfigured.matches(contextWith(null), metadata))
    }

    @Test
    fun `Redis 템플릿 빈이 없으면 인메모리 저장소를 쓴다`() {
        val beans = StaticListableBeanFactory()

        val templates: ObjectProvider<StringRedisTemplate> = beans.getBeanProvider(StringRedisTemplate::class.java)

        val store: LocationStore = ShareStoreConfig().locationStore(templates, Clock.systemUTC())

        assertThat(store).isInstanceOf(InMemoryLocationStore::class.java)
    }

    @Test
    fun `Redis 템플릿 빈이 있으면 Redis 저장소를 쓴다`() {
        val beans = StaticListableBeanFactory()
        beans.addBean("shareRedisTemplate", mock<StringRedisTemplate>())

        val templates: ObjectProvider<StringRedisTemplate> = beans.getBeanProvider(StringRedisTemplate::class.java)

        val store: LocationStore = ShareStoreConfig().locationStore(templates, Clock.systemUTC())

        assertThat(store).isInstanceOf(RedisLocationStore::class.java)
    }
}
