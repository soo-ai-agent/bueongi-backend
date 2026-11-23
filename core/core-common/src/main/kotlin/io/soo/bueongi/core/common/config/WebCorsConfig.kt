package io.soo.bueongi.core.common.config

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
        val origins: ArrayList<String> = ArrayList()
        for (origin in allowedOrigins.split(",")) {
            val trimmed: String = origin.trim()
            if (trimmed.isNotEmpty()) {
                origins.add(trimmed)
            }
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
