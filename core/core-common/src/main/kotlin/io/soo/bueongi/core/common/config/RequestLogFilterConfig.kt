package io.soo.bueongi.core.common.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RequestLogFilterConfig {
    @Bean
    fun requestLogFilterRegistration(): FilterRegistrationBean<RequestLogFilter> {
        val registration: FilterRegistrationBean<RequestLogFilter> = FilterRegistrationBean(RequestLogFilter())
        registration.addUrlPatterns("/*")
        registration.setName("requestLogFilter")
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}
