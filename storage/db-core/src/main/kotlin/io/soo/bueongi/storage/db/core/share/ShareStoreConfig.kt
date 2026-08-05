package io.soo.bueongi.storage.db.core.share

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.net.URI
import java.time.Clock



/**
 * 위치 공유 저장소([LocationStore]) 배선. **빈 env 함정**을 피하려고 `@ConditionalOnProperty`(빈 문자열도 "있음"으로 보는 경우가 있음)
 * 대신 [RedisConfigured]/[RedisNotConfigured] 라는 상호배타 [Condition] 으로 정확히 한쪽만 [LocationStore] 빈을 만든다 —
 * null 반환 빈 + `@ConditionalOnMissingBean` 의 순서 의존 함정도 회피한다(어느 쪽이든 `locationStore` 빈이 정확히 1개).
 *
 * `RedisAutoConfiguration` 은 [io.soo.bueongi.BueongiApplication] 에서 제외했다 — 부트 기본 자동설정은 URL 미지정 시에도
 * `localhost:6379` 로 연결을 시도하므로, 접속 팩토리를 여기서 명시적으로만(Redis 모드에서만) 생성한다.
 */
@Configuration
class ShareStoreConfig {

    /** Redis 모드: Lettuce 접속 팩토리 → 템플릿 → [RedisLocationStore]. 팩토리는 Spring 이 종료 시 close(destroy)한다. */
    @Configuration
    @Conditional(RedisConfigured::class)
    class RedisShareStore(
        @Value("\${bueongi.share.redis-url}") private val redisUrl: String,
    ) {
        @Bean(destroyMethod = "destroy")
        fun shareRedisConnectionFactory(): LettuceConnectionFactory {
            val uri: URI = URI(redisUrl)
            val port: Int = if (uri.port > 0) {
                uri.port
            } else {
                DEFAULT_REDIS_PORT
            }
            val config: RedisStandaloneConfiguration = RedisStandaloneConfiguration(uri.host ?: "localhost", port)
            // userInfo("user:password")에서 비밀번호만 추출 — 구분자가 없거나 비어 있으면 무인증 접속.
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
        fun locationStore(template: StringRedisTemplate, clock: Clock): LocationStore =
            RedisLocationStore(template, clock)
    }

    /** 폴백 모드: Redis 미주입(로컬/CI/MVP). 동일 [LocationStore] 계약이라 서비스/테스트는 무차별. */
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
