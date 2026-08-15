package io.soo.bueongi.storage.db.core.requestmetric

import io.soo.bueongi.storage.db.core.share.RedisConfigured
import io.soo.bueongi.storage.db.core.share.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RequestMetricStoreConfig {

    @Bean
    @Conditional(RedisConfigured::class)
    fun redisRequestMetricStore(template: StringRedisTemplate): RequestMetricStore = RedisRequestMetricStore(template)

    @Bean
    @Conditional(RedisNotConfigured::class)
    fun inMemoryRequestMetricStore(): RequestMetricStore = InMemoryRequestMetricStore()
}
