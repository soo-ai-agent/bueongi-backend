package io.soo.bueongi.storage.db.core.apilog

import io.soo.bueongi.storage.db.core.share.RedisConfigured
import io.soo.bueongi.storage.db.core.share.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class ApiCallLogStoreConfig {

    @Configuration
    @Conditional(RedisConfigured::class)
    class RedisApiCallLog {
        @Bean
        fun apiCallLogStore(template: StringRedisTemplate): ApiCallLogStore = RedisApiCallLogStore(template)
    }

    @Configuration
    @Conditional(RedisNotConfigured::class)
    class InMemoryApiCallLog {
        @Bean
        fun apiCallLogStore(): ApiCallLogStore = InMemoryApiCallLogStore()
    }
}
