package io.soo.bueongi.config

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
        val origins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        registry.addMapping("/api/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
        // 위치 공유: 보호자 웹이 서버 HTML(same-origin)로 폴링하면 CORS 가 불필요하나,
        // 별도 origin 의 보호자 웹(frontend-src)이 GET /share/{token}/location 을 폴링하는 구성도 지원하도록 함께 허용.
        registry.addMapping("/share/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
        // 정적 안심시설 JSON(/cdn/**)은 전부 공개 공공데이터 산출물 — dev 로컬 CDN 미들웨어와 동일하게
        // 모든 origin 의 GET 을 허용한다(자격증명 없음 → 와일드카드 무해).
        registry.addMapping("/cdn/**")
            .allowedOrigins("*")
            .allowedMethods("GET")
    }
}
