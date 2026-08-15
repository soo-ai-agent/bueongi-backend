package io.soo.bueongi.storage.db.core.apilog

import io.soo.bueongi.storage.db.core.share.store.RedisConfigured
import io.soo.bueongi.storage.db.core.share.store.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class ApiCallLogStoreConfig {

    @Bean
    @Conditional(RedisConfigured::class)
    fun redisApiCallLogStore(template: StringRedisTemplate): ApiCallLogStore =
        RedisApiCallLogStore(template)

    @Bean
    @Conditional(RedisNotConfigured::class)
    fun inMemoryApiCallLogStore(): ApiCallLogStore =
        InMemoryApiCallLogStore()
}
