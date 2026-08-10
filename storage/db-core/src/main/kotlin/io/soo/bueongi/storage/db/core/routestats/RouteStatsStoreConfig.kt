package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.storage.db.core.share.RedisConfigured
import io.soo.bueongi.storage.db.core.share.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RouteStatsStoreConfig {

    @Configuration
    @Conditional(RedisConfigured::class)
    class RedisRouteStats {
        @Bean
        fun routeStatsStore(template: StringRedisTemplate): RouteStatsStore = RedisRouteStatsStore(template)
    }

    @Configuration
    @Conditional(RedisNotConfigured::class)
    class InMemoryRouteStats {
        @Bean
        fun routeStatsStore(): RouteStatsStore = InMemoryRouteStatsStore()
    }
}
