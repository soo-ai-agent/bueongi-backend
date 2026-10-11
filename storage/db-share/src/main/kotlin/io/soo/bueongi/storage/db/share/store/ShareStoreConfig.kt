package io.soo.bueongi.storage.db.share.store

import org.springframework.beans.factory.ObjectProvider
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
    // 같은 이름의 빈을 조건부 설정 두 곳에 나눠 두면 IDE 가 조건을 못 읽어 "빈이 둘"로 경고한다 — 메서드 하나에서 고른다.
    @Bean
    fun locationStore(redisTemplate: ObjectProvider<StringRedisTemplate>, clock: Clock): LocationStore {
        val template: StringRedisTemplate = redisTemplate.ifAvailable ?: return InMemoryLocationStore(clock)
        return RedisLocationStore(template, clock)
    }

    @Configuration
    @Conditional(RedisConfigured::class)
    class RedisShareStore(
        @param:Value("\${bueongi.share.redis-url}") private val redisUrl: String,
    ) {
        @Bean(destroyMethod = "destroy")
        fun shareRedisConnectionFactory(): LettuceConnectionFactory {
            val uri: URI = URI(redisUrl)
            var port: Int = DEFAULT_REDIS_PORT
            if (uri.port > 0) {
                port = uri.port
            }
            val config: RedisStandaloneConfiguration = RedisStandaloneConfiguration(uri.host ?: "localhost", port)
            val password: String = uri.userInfo?.substringAfter(':', "").orEmpty()
            if (password.isNotEmpty()) {
                config.setPassword(password)
            }
            return LettuceConnectionFactory(config).apply { afterPropertiesSet() }
        }

        @Bean
        fun shareRedisTemplate(factory: LettuceConnectionFactory): StringRedisTemplate {
            return StringRedisTemplate(factory).apply { afterPropertiesSet() }
        }
    }

    private companion object {
        const val DEFAULT_REDIS_PORT: Int = 6379
    }
}
