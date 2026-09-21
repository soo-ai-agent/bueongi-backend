package io.soo.bueongi.storage.db.core.externalcall

import io.soo.bueongi.storage.db.core.share.store.RedisConfigured
import io.soo.bueongi.storage.db.core.share.store.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class ExternalCallMetricStoreConfig {
    @Bean
    @Conditional(RedisConfigured::class)
    fun redisExternalCallMetricStore(template: StringRedisTemplate): ExternalCallMetricStore = RedisExternalCallMetricStore(template)

    @Bean
    @Conditional(RedisNotConfigured::class)
    fun inMemoryExternalCallMetricStore(): ExternalCallMetricStore = InMemoryExternalCallMetricStore()
}
