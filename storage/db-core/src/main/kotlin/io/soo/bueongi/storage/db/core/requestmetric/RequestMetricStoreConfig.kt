package io.soo.bueongi.storage.db.core.requestmetric

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RequestMetricStoreConfig(@param:Value("\${bueongi.share.redis-url:}") private val redisUrl: String) {
    // 관리자 창(AdminRequestMetricStore)도 RequestMetricStore 라, 그냥 이 타입을 주입받는 쪽은 사용자 창을 받게 한다.
    @Bean
    @Primary
    fun requestMetricStore(template: ObjectProvider<StringRedisTemplate>): RequestMetricStore {
        if (redisUrl.isBlank()) {
            return InMemoryRequestMetricStore()
        }
        return RedisRequestMetricStore(template.getObject())
    }

    @Bean
    fun adminRequestMetricStore(template: ObjectProvider<StringRedisTemplate>): AdminRequestMetricStore {
        if (redisUrl.isBlank()) {
            return InMemoryAdminRequestMetricStore()
        }
        return RedisAdminRequestMetricStore(template.getObject())
    }
}
