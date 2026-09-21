package io.soo.bueongi.storage.db.core.share.store

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.net.URI
import java.time.Clock

// RedisAutoConfiguration 은 BueongiApplication 에서 제외돼 있다 — URL 미지정 시 localhost:6379 자동 연결을 막으려는 것.
@Configuration
class ShareStoreConfig {
    @Configuration
    @Conditional(RedisConfigured::class)
    class RedisShareStore(
        @param:Value("\${bueongi.share.redis-url}") private val redisUrl: String,
    ) {
        @Bean(destroyMethod = "destroy")
        fun shareRedisConnectionFactory(): LettuceConnectionFactory {
            val uri = URI(redisUrl)
            val port: Int =
                if (uri.port > 0) {
                    uri.port
                } else {
                    DEFAULT_REDIS_PORT
                }
            val config = RedisStandaloneConfiguration(uri.host ?: "localhost", port)
            val password: String = uri.userInfo?.substringAfter(':', "").orEmpty()
            if (password.isNotEmpty()) {
                config.setPassword(password)
            }
            return LettuceConnectionFactory(config).apply { afterPropertiesSet() }
        }

        @Bean
        fun shareRedisTemplate(factory: LettuceConnectionFactory): StringRedisTemplate =
            StringRedisTemplate(factory).apply { afterPropertiesSet() }

        @Bean
        fun locationStore(
            template: StringRedisTemplate,
            clock: Clock,
        ): LocationStore = RedisLocationStore(template, clock)
    }

    @Configuration
    @Conditional(RedisNotConfigured::class)
    class InMemoryShareStore {
        @Bean
        fun locationStore(clock: Clock): LocationStore = InMemoryLocationStore(clock)
    }

    private companion object {
        const val DEFAULT_REDIS_PORT: Int = 6379
    }
}
