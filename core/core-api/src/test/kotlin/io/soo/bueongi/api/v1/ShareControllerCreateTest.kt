package io.soo.bueongi.api.v1

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post

class ShareControllerCreateTest : ShareControllerTestSupport() {

    @Test
    fun `POST create 는 200 과 token·share_url·expires_at 을 돌려준다`() {
        mockMvc.post("/share/create") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"expires_in_hours\": 2}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
            jsonPath("$.owner_secret") { exists() }
            jsonPath("$.share_url") { exists() }
            jsonPath("$.expires_at") { exists() }
        }
    }

    @Test
    fun `본문 없는 create 도 기본 TTL 로 200`() {
        mockMvc.post("/share/create").andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
        }
    }

    @Test
    fun `expires_in_hours 범위 밖이면 400 VALIDATION_FAILED`() {
        mockMvc.post("/share/create") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"expires_in_hours\": 4}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("expiresInHours") }
        }
    }
}
