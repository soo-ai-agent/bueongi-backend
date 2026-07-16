package io.soo.bueongi.storage.db.core.adevent

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 광고 노출 저장소([AdEventStore]) 배선. 수신 요청 메트릭 저장소
 * ([io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStoreConfig])는 `bueongi.share.redis-url` 유무로
 * Redis/인메모리를 가르지만, 광고 노출은 이번 범위에서 **인메모리 구현만** 제공한다(MVP). Redis 확장이
 * 필요해지면 RequestMetricStoreConfig 처럼 RedisConfigured/RedisNotConfigured 조건으로 가르면 된다.
 */
@Configuration
class AdEventStoreConfig {
    @Bean
    fun adEventStore(): AdEventStore = InMemoryAdEventStore()
}
