package io.soo.bueongi.storage.db.share.store

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock

// Redis 연결은 Spring Boot 자동 설정(spring.data.redis.url, db-share.yml)이 만든다.
@Configuration
class ShareStoreConfig {
    @Bean
    fun locationStore(redisTemplate: StringRedisTemplate, clock: Clock): ShareSessionStore {
        return RedisShareSessionStore(redisTemplate, clock)
    }
}
