package io.soo.bueongi.core.app

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@Tag("context")
class ShareControllerLocationTest : ShareControllerTestSupport() {
    @Test
    fun `위치 POST 후 GET location 으로 같은 좌표를 200 으로 회수`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.04, \"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.updated_at") { exists() }
            jsonPath("$.expires_at") { exists() }
        }

        mockMvc.get("/share/$token/location").andExpect {
            status { isOk() }
            jsonPath("$.lat") { value(37.5) }
            jsonPath("$.lng") { value(127.04) }
            jsonPath("$.expired") { value(false) }
        }
    }

    @Test
    fun `GET location 의 갱신 시각은 snake_case updated_at 로 직렬화된다(guardian html 폴링 계약)`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.04, \"owner_secret\": \"$secret\"}"
        }.andExpect { status { isOk() } }

        mockMvc.get("/share/$token/location").andExpect {
            status { isOk() }
            jsonPath("$.updated_at") { exists() }
            jsonPath("$.updatedAt") { doesNotExist() }
        }
    }

    @Test
    fun `공유는 있으나 첫 위치 전이면 GET location 은 200 expired=false 에 좌표 null`() {
        val token: String = createShare()
        mockMvc.get("/share/$token/location").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(false) }
            jsonPath("$.lat") { value(null) }
        }
    }

    @Test
    fun `없는 token GET location 은 200 expired=true`() {
        mockMvc.get("/share/does-not-exist/location").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(true) }
            jsonPath("$.lat") { value(null) }
        }
    }

    @Test
    fun `만료 후 GET location 은 200 expired=true`() {
        val token: String = createShare("{\"expires_in_hours\": 1}")
        clock.advanceSeconds(3600)
        mockMvc.get("/share/$token/location").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(true) }
        }
    }

    @Test
    fun `POST end 후 GET location 은 200 expired=true (도착·중단 시 공유 종료)`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.04, \"owner_secret\": \"$secret\"}"
        }.andExpect { status { isOk() } }

        mockMvc.post("/share/$token/end") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"$secret\"}"
        }.andExpect { status { isOk() } }

        mockMvc.get("/share/$token/location").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(true) }
            jsonPath("$.lat") { value(null) }
        }
    }

    @Test
    fun `없는 token POST end 도 200 (멱등)`() {
        mockMvc.post("/share/does-not-exist/end") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"x\"}"
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `범위 밖 좌표 POST 는 400 VALIDATION_FAILED`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 91.0, \"lng\": 127.0, \"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `없는 token 에 위치 POST 는 404 SHARE_SESSION_NOT_FOUND`() {
        mockMvc.post("/share/does-not-exist/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.0, \"owner_secret\": \"x\"}"
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_SESSION_NOT_FOUND") }
        }
    }

    @Test
    fun `잘못된 owner_secret 으로 위치 POST 는 404(위치 조작 차단)`() {
        val (token, _) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.0, \"owner_secret\": \"wrong-secret\"}"
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_SESSION_NOT_FOUND") }
        }
    }

    @Test
    fun `owner_secret 누락 위치 POST 는 400 VALIDATION_FAILED`() {
        val (token, _) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.0}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `remaining 포함 위치 POST 후 GET location 은 remaining_m·remaining_s 를 돌려준다`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.0, \"owner_secret\": \"$secret\", \"remaining_m\": 500.0, \"remaining_s\": 360.0}"
        }.andExpect { status { isOk() } }

        mockMvc.get("/share/$token/location").andExpect {
            status { isOk() }
            jsonPath("$.remaining_m") { value(500.0) }
            jsonPath("$.remaining_s") { value(360.0) }
        }
    }

    @Test
    fun `음수 remaining_m 위치 POST 는 400 VALIDATION_FAILED`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.0, \"owner_secret\": \"$secret\", \"remaining_m\": -1.0}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }
}
