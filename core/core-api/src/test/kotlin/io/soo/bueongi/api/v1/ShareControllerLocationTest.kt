package io.soo.bueongi.api.v1

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * /share/{token}/location 왕복(POST 갱신 → GET 회수)과 종료(POST end), 그리고 좌표·owner_secret·remaining
 * 검증 및 만료/404 분기를 고정한다.
 */
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
        // guardian.html 의 renderLive(data) 는 `data.updated_at`(snake_case)로 '마지막 갱신' 시각을 그린다(guardian.html:84).
        // LocationView.updatedAt 은 @JsonProperty("updated_at") 로 그 계약을 맞춘다. 그런데 위 좌표 회수 테스트는
        // lat/lng/expired 만 단언하고 updated_at 필드명은 미검증이다(POST location 의 updated_at 단언은 보호자가 읽지 않는
        // 다른 DTO=UpdateLocationResponse 다). 따라서 누가 LocationView 에서 @JsonProperty 를 "정리"로 제거해 서버가
        // camelCase `updatedAt` 으로 직렬화해도 모든 테스트가 통과하지만, 서버가 서빙하는 보호자 페이지의 `data.updated_at`
        // 이 undefined 가 되어 '마지막 갱신' 표시가 조용히 사라진다(backend 가 소유한 client 의 깨진 계약). 이를 고정한다.
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": 37.5, \"lng\": 127.04, \"owner_secret\": \"$secret\"}"
        }.andExpect { status { isOk() } }

        mockMvc.get("/share/$token/location").andExpect {
            status { isOk() }
            // guardian.html:84 가 읽는 정확한 필드명.
            jsonPath("$.updated_at") { exists() }
            // @JsonProperty 회귀(제거/약화) 시 나타날 camelCase 형태가 응답에 없어야 한다.
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
            content = "{\"lat\": 91.0, \"lng\": 127.0, \"owner_secret\": \"$secret\"}" // lat > 90
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `비유한 좌표 리터럴 POST 는 400 MALFORMED_REQUEST`() {
        val token: String = createShare()
        mockMvc.post("/share/$token/location") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"lat\": NaN, \"lng\": 127.0}" // 비숫자 리터럴 → Jackson 파싱 거부
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MALFORMED_REQUEST") }
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
            content = "{\"lat\": 37.5, \"lng\": 127.0}" // owner_secret 없음
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
