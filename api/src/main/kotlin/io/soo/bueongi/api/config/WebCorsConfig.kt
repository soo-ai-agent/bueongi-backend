package io.soo.bueongi.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebCorsConfig(
    @param:Value("\${bueongi.cors.allowed-origins}")
    private val allowedOrigins: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val origins: List<String> =
            allowedOrigins.split(",").map {
                it.trim()
            }.filter {
                it.isNotEmpty()
            }
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
