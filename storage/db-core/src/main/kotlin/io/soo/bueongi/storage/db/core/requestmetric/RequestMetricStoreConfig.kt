package io.soo.bueongi.storage.db.core.requestmetric

import io.soo.bueongi.storage.db.core.share.store.RedisConfigured
import io.soo.bueongi.storage.db.core.share.store.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RequestMetricStoreConfig {
    // 관리자 창도 RequestMetricStore 라, 그냥 이 타입을 주입받는 쪽은 사용자 창을 받게 한다.
    @Bean
    @Primary
    @Conditional(RedisConfigured::class)
    fun redisRequestMetricStore(template: StringRedisTemplate): RequestMetricStore = RedisRequestMetricStore(template)

    @Bean
    @Primary
    @Conditional(RedisNotConfigured::class)
    fun inMemoryRequestMetricStore(): RequestMetricStore = InMemoryRequestMetricStore()

    @Bean
    @Conditional(RedisConfigured::class)
    fun redisAdminRequestMetricStore(template: StringRedisTemplate): AdminRequestMetricStore = RedisAdminRequestMetricStore(template)

    @Bean
    @Conditional(RedisNotConfigured::class)
    fun inMemoryAdminRequestMetricStore(): AdminRequestMetricStore = InMemoryAdminRequestMetricStore()
}
