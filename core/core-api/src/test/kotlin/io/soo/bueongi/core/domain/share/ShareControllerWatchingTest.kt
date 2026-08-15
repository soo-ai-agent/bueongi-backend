package io.soo.bueongi.core.domain.share

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class ShareControllerWatchingTest : ShareControllerTestSupport() {

    @Test
    fun `POST watching 은 보호자 GET 전엔 false, 후엔 true (owner_secret 필요)`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/watching") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.watching") { value(false) }
        }
        mockMvc.get("/share/$token/location").andExpect { status { isOk() } }
        mockMvc.post("/share/$token/watching") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.watching") { value(true) }
        }
    }

    @Test
    fun `POST watching 은 현재 공유 위치도 함께 돌려준다(관리자 미리보기)`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.55, \"lng\": 127.02, \"owner_secret\": \"$secret\"}"
        }.andExpect { status { isOk() } }

        mockMvc.post("/share/$token/watching") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.lat") { value(37.55) }
            jsonPath("$.lng") { value(127.02) }
            jsonPath("$.updated_at") { exists() }
            jsonPath("$.expired") { value(false) }
            jsonPath("$.watching") { value(false) }
        }
    }

    @Test
    fun `POST watching 잘못된 owner_secret 은 404`() {
        val (token, _) = createSharePair()
        mockMvc.post("/share/$token/watching") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"wrong\"}"
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_SESSION_NOT_FOUND") }
        }
    }
}
