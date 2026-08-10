package io.soo.bueongi.storage.db.core.requestmetric

import io.soo.bueongi.storage.db.core.share.RedisConfigured
import io.soo.bueongi.storage.db.core.share.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RequestMetricStoreConfig {

    @Configuration
    @Conditional(RedisConfigured::class)
    class RedisRequestMetric {
        @Bean
        fun requestMetricStore(template: StringRedisTemplate): RequestMetricStore = RedisRequestMetricStore(template)
    }

    @Configuration
    @Conditional(RedisNotConfigured::class)
    class InMemoryRequestMetric {
        @Bean
        fun requestMetricStore(): RequestMetricStore = InMemoryRequestMetricStore()
    }
}
