package io.soo.bueongi.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 프론트(별도 포트 3619)에서 백엔드(8119)를 직접 호출하므로 CORS 를 명시 허용한다.
 * 허용 origin 은 설정(bueongi.cors.allowed-origins)에서 주입 — 기본은 dev 서버만.
 */
@Configuration
class WebCorsConfig(
    @Value("\${bueongi.cors.allowed-origins}") private val allowedOrigins: String,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        val origins: List<String> = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        registry.addMapping("/api/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
        registry.addMapping("/share/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
        registry.addMapping("/cdn/**")
            .allowedOrigins("*")
            .allowedMethods("GET")
    }
}
