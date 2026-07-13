package io.soo.bueongi.api.config

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * 수신 요청 계측 필터([RequestMetricsFilter]) 배선. `/api`·`/share` 하위에만 매핑하고, 세부 제외
 * (`/api/admin` 하위·`/actuator`·`/healthz`·`/cdn`)는 필터의 `shouldNotFilter` 가 담당한다.
 *
 * 필터체인 최상단([Ordered.HIGHEST_PRECEDENCE])에 두어 보안·디스패치 전 구간의 소요·최종 status 를 감싸 잰다.
 * servlet 웹 컨텍스트에서만 등록한다(NONE·슬라이스 컨텍스트는 servlet 컨테이너가 없어 필터 등록이 무의미).
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RequestMetricsFilterConfig {

    @Bean
    fun requestMetricsFilterRegistration(
        requestMetricStore: RequestMetricStore,
    ): FilterRegistrationBean<RequestMetricsFilter> {
        val registration = FilterRegistrationBean(RequestMetricsFilter(requestMetricStore))
        registration.addUrlPatterns("/api/*", "/share/*")
        registration.setName("requestMetricsFilter")
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}
