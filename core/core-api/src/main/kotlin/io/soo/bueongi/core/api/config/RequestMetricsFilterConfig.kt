package io.soo.bueongi.core.api.config

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RequestMetricsFilterConfig {

    @Bean
    fun requestMetricsFilterRegistration(
        requestMetricStore: RequestMetricStore,
    ): FilterRegistrationBean<RequestMetricsFilter> {
        val registration: FilterRegistrationBean<RequestMetricsFilter> = FilterRegistrationBean(RequestMetricsFilter(requestMetricStore))
        registration.addUrlPatterns("/api/*", "/share/*")
        registration.setName("requestMetricsFilter")
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}
