package io.soo.bueongi.core.app

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@Tag("context")
class ShareControllerRouteTest : ShareControllerTestSupport() {
    private fun routeBody(
        secret: String,
        markerType: String = "cctv",
        type: String = "safe",
    ) =
        """
        {
          "owner_secret": "$secret",
          "destination": { "name": "집", "lat": 37.56, "lng": 126.97 },
          "path": [ {"lat": 37.5, "lng": 127.0}, {"lat": 37.55, "lng": 127.02} ],
          "markers": [ {"type": "$markerType", "lat": 37.5, "lng": 127.0, "x": 50.0, "y": 50.0, "name": "CCTV", "purpose": "생활방범", "camera_count": 2, "address": "서울시", "phone": null} ],
          "total_distance_m": 1234,
          "total_time_s": 900,
          "type": "$type"
        }
        """.trimIndent()

    @Test
    fun `POST route 후 GET route 로 같은 컨텍스트를 200 으로 회수`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody(secret)
        }.andExpect {
            status { isOk() }
            jsonPath("$.updated_at") { exists() }
            jsonPath("$.expires_at") { exists() }
        }

        mockMvc.get("/share/$token/route").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(false) }
            jsonPath("$.route.destination.name") { value("집") }
            jsonPath("$.route.destination.lat") { value(37.56) }
            jsonPath("$.route.path.length()") { value(2) }
            jsonPath("$.route.markers[0].type") { value("cctv") }
            jsonPath("$.route.markers[0].camera_count") { value(2) }
            jsonPath("$.route.total_distance_m") { value(1234) }
            jsonPath("$.route.total_time_s") { value(900) }
        }
    }

    @Test
    fun `경로 미저장 시 GET route 는 200 expired=false 에 route null`() {
        val token: String = createShare()
        mockMvc.get("/share/$token/route").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(false) }
            jsonPath("$.route") { value(null) }
        }
    }

    @Test
    fun `없는 token GET route 는 200 expired=true 에 route null`() {
        mockMvc.get("/share/does-not-exist/route").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(true) }
            jsonPath("$.route") { value(null) }
        }
    }

    @Test
    fun `만료 후 GET route 는 200 expired=true`() {
        val (token, secret) = createSharePair("{\"expires_in_hours\": 1}")
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody(secret)
        }.andExpect { status { isOk() } }
        clock.advanceSeconds(3600)
        mockMvc.get("/share/$token/route").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(true) }
            jsonPath("$.route") { value(null) }
        }
    }

    @Test
    fun `없는 token 에 POST route 는 404 SHARE_SESSION_NOT_FOUND`() {
        mockMvc.post("/share/does-not-exist/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody("x")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_SESSION_NOT_FOUND") }
        }
    }

    @Test
    fun `잘못된 owner_secret 으로 POST route 는 404(경로 조작 차단)`() {
        val (token, _) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody("wrong-secret")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_SESSION_NOT_FOUND") }
        }
    }

    @Test
    fun `허용되지 않은 마커 type 은 400 VALIDATION_FAILED`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "owner_secret": "$secret",
                  "destination": { "lat": 37.56, "lng": 126.97 },
                  "markers": [ {"type": "start", "lat": 37.5, "lng": 127.0} ]
                }
                """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `경로 API 가 내려주는 보안등 마커 type 은 저장된다`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody(secret, markerType = "security_lamp")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `POST route 의 type 은 GET route 에 그대로 실린다`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody(secret, type = "main")
        }.andExpect { status { isOk() } }

        mockMvc.get("/share/$token/route").andExpect {
            status { isOk() }
            jsonPath("$.route.type") { value("main") }
        }
    }

    @Test
    fun `허용되지 않은 route type 은 422 INVALID_ROUTE_META`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody(secret, type = "scenic")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_ROUTE_META") }
            jsonPath("$.errors[0].field") { value("type") }
        }
    }

    @Test
    fun `GET route 는 시청자로 집계되지 않는다(위치 폴링과 분리)`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody(secret)
        }.andExpect { status { isOk() } }
        repeat(3) { mockMvc.get("/share/$token/route").andExpect { status { isOk() } } }
        mockMvc.post("/share/$token/watching") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.watching") { value(false) }
        }
    }
}
