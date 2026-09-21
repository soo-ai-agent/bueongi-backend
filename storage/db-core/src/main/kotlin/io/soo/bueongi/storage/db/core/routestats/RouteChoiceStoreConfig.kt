package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.storage.db.core.share.store.RedisConfigured
import io.soo.bueongi.storage.db.core.share.store.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RouteChoiceStoreConfig {
    @Bean
    @Conditional(RedisConfigured::class)
    fun redisRouteChoiceStore(template: StringRedisTemplate): RouteChoiceStore = RedisRouteChoiceStore(template)

    @Bean
    @Conditional(RedisNotConfigured::class)
    fun inMemoryRouteChoiceStore(): RouteChoiceStore = InMemoryRouteChoiceStore()
}
