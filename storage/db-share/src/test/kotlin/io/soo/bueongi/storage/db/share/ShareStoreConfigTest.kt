package io.soo.bueongi.storage.db.share

import io.soo.bueongi.storage.db.share.store.DbConfigured
import io.soo.bueongi.storage.db.share.store.InMemoryLocationStore
import io.soo.bueongi.storage.db.share.store.JpaLocationStore
import io.soo.bueongi.storage.db.share.store.LocationStore
import io.soo.bueongi.storage.db.share.store.RedisConfigured
import io.soo.bueongi.storage.db.share.store.RedisLocationStore
import io.soo.bueongi.storage.db.share.store.ShareSessionRepository
import io.soo.bueongi.storage.db.share.store.ShareStoreConfig
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
    private val dbConfigured = DbConfigured()
    private val metadata = mock<AnnotatedTypeMetadata>()

    private fun contextWith(key: String, value: String?): ConditionContext {
        val env = MockEnvironment()
        if (value != null) {
            env.setProperty(key, value)
        }
        return mock { on { environment } doReturn env }
    }

    private fun contextWith(redisUrl: String?): ConditionContext {
        return contextWith("bueongi.share.redis-url", redisUrl)
    }

    private fun locationStoreOf(beans: StaticListableBeanFactory): LocationStore {
        val repositories: ObjectProvider<ShareSessionRepository> = beans.getBeanProvider(ShareSessionRepository::class.java)
        val redisTemplates: ObjectProvider<StringRedisTemplate> = beans.getBeanProvider(StringRedisTemplate::class.java)
        return ShareStoreConfig().locationStore(repositories, redisTemplates, Clock.systemUTC())
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
        val store: LocationStore = locationStoreOf(StaticListableBeanFactory())

        assertThat(store).isInstanceOf(InMemoryLocationStore::class.java)
    }

    @Test
    fun `Redis 템플릿 빈이 있으면 Redis 저장소를 쓴다`() {
        val beans = StaticListableBeanFactory()
        beans.addBean("shareRedisTemplate", mock<StringRedisTemplate>())

        val store: LocationStore = locationStoreOf(beans)

        assertThat(store).isInstanceOf(RedisLocationStore::class.java)
    }

    @Test
    fun `db-url 이 채워지면 DB 모드를 켠다`() {
        assertTrue(dbConfigured.matches(contextWith("bueongi.share.db-url", "jdbc:h2:mem:test"), metadata))
    }

    @Test
    fun `빈 db-url 이면 DB 모드를 켜지 않는다`() {
        assertFalse(dbConfigured.matches(contextWith("bueongi.share.db-url", ""), metadata))
        assertFalse(dbConfigured.matches(contextWith("bueongi.share.db-url", null), metadata))
    }

    @Test
    fun `공유 세션 리포지토리 빈이 있으면 DB 저장소를 쓴다`() {
        val beans = StaticListableBeanFactory()
        beans.addBean("shareSessionRepository", mock<ShareSessionRepository>())

        val store: LocationStore = locationStoreOf(beans)

        assertThat(store).isInstanceOf(JpaLocationStore::class.java)
    }

    @Test
    fun `DB 와 Redis 빈이 둘 다 있으면 DB 가 이긴다`() {
        val beans = StaticListableBeanFactory()
        beans.addBean("shareSessionRepository", mock<ShareSessionRepository>())
        beans.addBean("shareRedisTemplate", mock<StringRedisTemplate>())

        val store: LocationStore = locationStoreOf(beans)

        assertThat(store).isInstanceOf(JpaLocationStore::class.java)
    }
}
