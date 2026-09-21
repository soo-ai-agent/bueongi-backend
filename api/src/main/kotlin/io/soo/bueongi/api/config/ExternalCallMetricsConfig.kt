package io.soo.bueongi.api.config

import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetricStore
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ExternalCallMetricsConfig {

    @Bean
    fun externalCallMetricsCustomizer(
        externalCallMetricStore: ExternalCallMetricStore,
        clock: Clock,
    ): RestClientCustomizer =
        RestClientCustomizer { builder ->
            builder.requestInterceptor(ExternalCallMetricsInterceptor(externalCallMetricStore, clock))
        }
}
