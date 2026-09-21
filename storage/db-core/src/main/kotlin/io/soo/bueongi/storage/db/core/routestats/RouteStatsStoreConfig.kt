package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.storage.db.core.share.store.RedisConfigured
import io.soo.bueongi.storage.db.core.share.store.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RouteStatsStoreConfig {
    @Bean
    @Conditional(RedisConfigured::class)
    fun redisRouteStatsStore(template: StringRedisTemplate): RouteStatsStore = RedisRouteStatsStore(template)

    @Bean
    @Conditional(RedisNotConfigured::class)
    fun inMemoryRouteStatsStore(): RouteStatsStore = InMemoryRouteStatsStore()
}
