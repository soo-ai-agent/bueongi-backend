package io.soo.bueongi.api.v1

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * /share/{token}/route 왕복(POST 저장 → GET 회수)과 마커·좌표·destination·거리 검증, type/provenance 메타(에코·422),
 * 그리고 경로 조회의 시청자 비집계를 고정한다.
 */
class ShareControllerRouteTest : ShareControllerTestSupport() {

    /** 유효한 경로 저장 본문(owner_secret 치환). */
    private fun routeBody(secret: String) = """
        {
          "owner_secret": "$secret",
          "destination": { "name": "집", "lat": 37.56, "lng": 126.97 },
          "path": [ {"lat": 37.5, "lng": 127.0}, {"lat": 37.55, "lng": 127.02} ],
          "markers": [ {"type": "cctv", "lat": 37.5, "lng": 127.0, "x": 50.0, "y": 50.0, "name": "CCTV", "purpose": "생활방범", "camera_count": 2, "address": "서울시", "phone": null} ],
          "total_distance_m": 1234,
          "total_time_s": 900
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
            content = """
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
    fun `범위 밖 경로 좌표는 400 VALIDATION_FAILED`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "owner_secret": "$secret",
                  "destination": { "lat": 37.56, "lng": 126.97 },
                  "path": [ {"lat": 91.0, "lng": 127.0} ]
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `destination 누락 POST route 는 400 VALIDATION_FAILED`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `음수 total_distance_m POST route 는 400 VALIDATION_FAILED`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "owner_secret": "$secret",
                  "destination": { "lat": 37.56, "lng": 126.97 },
                  "total_distance_m": -1
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `type·provenance 포함 POST route 후 GET route 는 두 값을 그대로 에코`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "owner_secret": "$secret",
                  "destination": { "lat": 37.56, "lng": 126.97 },
                  "type": "safe",
                  "provenance": "서울시 안심이 CCTV 2026-03 기준"
                }
            """.trimIndent()
        }.andExpect { status { isOk() } }

        mockMvc.get("/share/$token/route").andExpect {
            status { isOk() }
            jsonPath("$.expired") { value(false) }
            jsonPath("$.route.type") { value("safe") }
            jsonPath("$.route.provenance") { value("서울시 안심이 CCTV 2026-03 기준") }
        }
    }

    @Test
    fun `type·provenance 생략 시 GET route 는 두 값이 null(하위호환)`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = routeBody(secret)
        }.andExpect { status { isOk() } }

        mockMvc.get("/share/$token/route").andExpect {
            status { isOk() }
            jsonPath("$.route.destination.name") { value("집") }
            jsonPath("$.route.type") { value(null) }
            jsonPath("$.route.provenance") { value(null) }
        }
    }

    @Test
    fun `허용되지 않은 route type 은 422 INVALID_ROUTE_META`() {
        val (token, secret) = createSharePair()
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "owner_secret": "$secret",
                  "destination": { "lat": 37.56, "lng": 126.97 },
                  "type": "scenic"
                }
            """.trimIndent()
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_ROUTE_META") }
            jsonPath("$.errors[0].field") { value("type") }
        }
    }

    @Test
    fun `길이 초과 provenance 는 422 INVALID_ROUTE_META`() {
        val (token, secret) = createSharePair()
        val tooLong: String = "가".repeat(201)
        mockMvc.post("/share/$token/route") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "owner_secret": "$secret",
                  "destination": { "lat": 37.56, "lng": 126.97 },
                  "provenance": "$tooLong"
                }
            """.trimIndent()
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_ROUTE_META") }
            jsonPath("$.errors[0].field") { value("provenance") }
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
