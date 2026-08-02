package io.soo.bueongi.api.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options

/**
 * [WebCorsConfig] 의 CORS 보안 불변식을 실 구성(application.yml 기본 `bueongi.cors.allowed-origins`)으로 e2e 고정한다.
 *
 * 안심 귀가 앱에서 CORS 오설정은 대표적 실 보안 결함이다(임의 origin 허용 → 타 사이트가 사용자 위치/경로 API 를 교차 호출,
 * 위험 메서드 허용 → 의도치 않은 변경 동사). 기존 테스트는 핵심 도메인·에러 계약만 고정했고 CORS 정책은 미고정이었다.
 * 다음 회귀를 차단한다:
 *  - 허용 origin 정책이 `allowedOrigins("*")` 등으로 넓어지는 것(미허용 origin 거부로 가드).
 *  - 허용 메서드 목록이 GET/POST/OPTIONS 밖(DELETE/PUT/PATCH)으로 넓어지는 것.
 * 풀 컨텍스트로 띄우는 이유: [WebCorsConfig] 는 @Configuration WebMvcConfigurer 라 @WebMvcTest 슬라이스에 자동 포함되지 않고,
 * `@Value` 로 application.yml 의 allowed-origins 를 실제 주입받아야 정책 그대로 검증되기 때문.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebCorsConfigTest(
    @Autowired private val mockMvc: MockMvc,
) {

    /** application.yml 기본 허용 origin(BUEONGI_CORS_ORIGINS 미설정 시). */
    private val allowedOrigin: String = "http://localhost:3619"

    @Test
    fun `허용 origin 의 GET 프리플라이트는 통과하고 메서드는 GET POST OPTIONS 로 제한된다`() {
        mockMvc.options("/api/places/search") {
            header("Origin", allowedOrigin)
            header("Access-Control-Request-Method", "GET")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", allowedOrigin) }
            header { string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("GET")) }
            header { string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")) }
            header { string("Access-Control-Allow-Methods", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DELETE"))) }
            header { string("Access-Control-Allow-Methods", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PUT"))) }
            header { string("Access-Control-Allow-Methods", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PATCH"))) }
        }
    }

    @Test
    fun `미허용 origin 의 프리플라이트는 거부된다 - 와일드카드 회귀 차단`() {
        mockMvc.options("/api/places/search") {
            header("Origin", "http://evil.example")
            header("Access-Control-Request-Method", "GET")
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist("Access-Control-Allow-Origin") }
        }
    }

    @Test
    fun `허용 origin 이라도 미허용 메서드 프리플라이트는 거부된다`() {
        mockMvc.options("/api/places/search") {
            header("Origin", allowedOrigin)
            header("Access-Control-Request-Method", "DELETE")
        }.andExpect {
            status { isForbidden() }
        }
    }
}
