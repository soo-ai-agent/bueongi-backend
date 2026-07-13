package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.storage.db.core.share.RedisConfigured
import io.soo.bueongi.storage.db.core.share.RedisNotConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * 경로 추천 집계 저장소([RouteStatsStore]) 배선. 접속 내역 저장소와 **동일한 선택 메커니즘**을 쓴다 —
 * `bueongi.share.redis-url` 유무를 상호배타 [RedisConfigured]/[RedisNotConfigured] 로 판정해 정확히 한쪽만 빈을 만든다.
 * Redis 모드에서는 [io.soo.bueongi.storage.db.core.share.ShareStoreConfig] 가 만든 `StringRedisTemplate` 을 재사용한다.
 */
@Configuration
class RouteStatsStoreConfig {

    /** Redis 모드: 공유 저장소와 같은 템플릿을 재사용해 단일 리스트 키에 적재. */
    @Configuration
    @Conditional(RedisConfigured::class)
    class RedisRouteStats {
        @Bean
        fun routeStatsStore(template: StringRedisTemplate): RouteStatsStore = RedisRouteStatsStore(template)
    }

    /** 폴백 모드: Redis 미주입(로컬/CI/MVP). 동일 계약이라 서비스/테스트는 무차별. */
    @Configuration
    @Conditional(RedisNotConfigured::class)
    class InMemoryRouteStats {
        @Bean
        fun routeStatsStore(): RouteStatsStore = InMemoryRouteStatsStore()
    }
}
