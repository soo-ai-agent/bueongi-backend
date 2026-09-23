package io.soo.bueongi.storage.db.core.routestats

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RouteStatsStoreConfig {
    // Redis 템플릿 빈은 redis-url 이 설정된 경우에만 존재한다(ShareStoreConfig) — ObjectProvider 로 유무를 흡수한다.
    @Bean
    fun routeStatsStore(
        @Value("\${bueongi.share.redis-url:}") redisUrl: String,
        template: ObjectProvider<StringRedisTemplate>,
    ): RouteStatsStore {
        if (redisUrl.isBlank()) {
            return InMemoryRouteStatsStore()
        }
        return RedisRouteStatsStore(template.getObject())
    }
}
