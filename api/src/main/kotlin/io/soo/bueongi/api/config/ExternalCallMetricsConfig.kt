package io.soo.bueongi.api.config

import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetricStore
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ExternalCallMetricsConfig {

    /**
     * 자동 설정된 `RestClient.Builder` 에 계측을 건다. clients 모듈은 모두 그 빌더를 주입받아
     * `clone().build()` 하므로, 여기 한 번 걸면 외부 호출 전부가 걸린다.
     */
    @Bean
    fun externalCallMetricsCustomizer(
        externalCallMetricStore: ExternalCallMetricStore,
        clock: Clock,
    ): RestClientCustomizer =
        RestClientCustomizer { builder ->
            builder.requestInterceptor(ExternalCallMetricsInterceptor(externalCallMetricStore, clock))
        }
}
