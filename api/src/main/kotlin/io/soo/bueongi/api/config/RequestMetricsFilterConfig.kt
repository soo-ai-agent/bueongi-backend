package io.soo.bueongi.api.config

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
        // 전 경로 — 스캐너는 /wp-login.php 처럼 우리 API 밖을 두드린다. 그것도 로그에 남아야 한다.
        registration.addUrlPatterns("/*")
        registration.setName("requestMetricsFilter")
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}
