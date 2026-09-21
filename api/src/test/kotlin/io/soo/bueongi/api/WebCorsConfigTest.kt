package io.soo.bueongi.api

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("context")
class WebCorsConfigTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
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
}
