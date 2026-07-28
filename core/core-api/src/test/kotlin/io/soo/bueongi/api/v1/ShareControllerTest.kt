package io.soo.bueongi.api.v1

import io.soo.bueongi.domain.share.MutableTestClock
import io.soo.bueongi.api.ApiControllerAdvice
import io.soo.bueongi.domain.share.LocationView
import io.soo.bueongi.domain.share.ShareService
import io.soo.bueongi.domain.share.UpdateLocationResponse
import io.soo.bueongi.storage.db.core.share.InMemoryLocationStore
import io.soo.bueongi.storage.db.core.share.LocationStore

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * [ShareController] 슬라이스 테스트(@WebMvcTest). MVP 위치 공유 4개 엔드포인트의 HTTP 계약(상태/응답 shape/에러 code)을
 * 고정한다. 실제 [ShareService] + [InMemoryLocationStore] + 가변 Clock 을 주입해 HTTP→서비스→저장소 왕복과
 * 만료(expired)·404·400 분기를 결정론적으로 검증한다(공통 [io.soo.bueongi.api.ApiControllerAdvice] 가 슬라이스에 포함됨).
 */
@WebMvcTest(ShareController::class)
// 보안 필터 비활성(슬라이스는 컨트롤러 계약만 검증 — 실 보안은 AdminAuthGateTest).
@AutoConfigureMockMvc(addFilters = false)
@Import(ShareControllerTest.ShareTestConfig::class)
@ActiveProfiles("test")
class ShareControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val clock: MutableTestClock,
) {

    /** POST 로 공유를 만들고 token(읽기 전용)을 돌려준다(조회 전용 테스트 헬퍼). */
    private fun createShare(body: String = "{}"): String {
        val response: String = mockMvc.post("/share/create") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response.contentAsString
        return Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
    }

    /** 공유를 만들고 (token, owner_secret) 둘 다 돌려준다(위치 쓰기/종료 테스트 헬퍼). */
    private fun createSharePair(body: String = "{}"): Pair<String, String> {
        val response: String = mockMvc.post("/share/create") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response.contentAsString
        val token: String = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
        val secret: String = Regex("\"owner_secret\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
        return token to secret
    }

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
            content = "{\"expires_in_hours\": 4}" // 1~3 초과
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("expiresInHours") }
        }
    }

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
    fun `GET 보호자 페이지는 200 text-html 이고 토큰이 주입된다`() {
        val token: String = createShare()
        mockMvc.get("/share/$token").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString(token)) }
            content { string(org.hamcrest.Matchers.containsString("leaflet")) }
        }
    }

    @Test
    fun `보호자 페이지는 토큰의 JS-주입 문자를 이스케이프해 리터럴을 탈출하지 못하게 한다`() {
        // 토큰은 path variable 이라 공격자가 임의 값을 줄 수 있고, guardian.html 의 `var TOKEN = "__SHARE_TOKEN__";`
        // (JS 큰따옴표 리터럴)에 그대로 치환된다. escapeJs 가 없으면 토큰 안의 `"` 가 리터럴을 닫고 임의 JS 가 실행되는
        // reflected XSS 가 된다(보호자가 메신저 링크로 여는 공개 페이지). 단일 세그먼트라 `/` 는 못 들어오지만
        // `";alert(1)<>` 처럼 `"`<`>` 는 한 세그먼트로 도달한다 → escapeJs 의 `"`→\" / `<`→< / `>`→> 분기를
        // 고정한다. page 가 깨끗한 base64url 토큰만 받던 기존 테스트는 escapeJs 가 no-op 으로만 돌아 이 방어가 미고정이었다.
        val malicious: String = "a\"b<>"
        mockMvc.get("/share/{token}", malicious).andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            // 세 위험문자가 모두 이스케이프된 정확한 안전 리터럴(리터럴/스크립트 탈출 불가).
            content { string(org.hamcrest.Matchers.containsString("var TOKEN = \"a\\\"b\\u003c\\u003e\";")) }
            // escapeJs 회귀(제거/약화) 시 나타날 리터럴 탈출 형태(`"` 가 그대로 닫힘)가 응답에 없어야 한다.
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("var TOKEN = \"a\"b"))) }
        }
    }

    @Test
    fun `보호자 페이지는 폴링 URL 에 토큰을 encodeURIComponent 로 감싸 경로주입을 막는다`() {
        // reflected-XSS/경로주입 방어의 클라이언트측 절반. escapeJs(서버측)는 토큰이 JS 리터럴을 탈출하지 못하게만 막고,
        // 그 토큰을 폴링 fetch URL 로 쓸 때의 경로 주입(예: `../`·`%2F`)은 client JS 의 encodeURIComponent 가 책임진다.
        // page() 는 HTML+JS 를 통째로 서빙하므로 이 client 방어도 같은 응답의 일부인데, 기존 page 테스트는
        // 이스케이프된 "리터럴"만 단언하고 폴링 URL 조립은 미검증 → 누가 guardian.html 의 fetch 를
        // `'/share/' + TOKEN + '/location'` 으로 "단순화"해 encodeURIComponent 를 제거해도 모든 테스트가 통과했다.
        // 서빙된 페이지가 토큰을 encodeURIComponent 로 감싸 폴링 URL 을 만드는지 고정해 그 회귀를 차단한다.
        val token: String = createShare()
        mockMvc.get("/share/$token").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString("encodeURIComponent(TOKEN)")) }
            // 인코딩 없이 토큰을 URL 에 직접 이어붙이는 회귀 형태가 응답에 없어야 한다.
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("'/share/' + TOKEN +"))) }
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
    fun `POST watching 은 보호자 GET 전엔 false, 후엔 true (owner_secret 필요)`() {
        val (token, secret) = createSharePair()
        // 보호자 조회 전 → 보는 사람 없음
        mockMvc.post("/share/$token/watching") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.watching") { value(false) }
        }
        // 보호자 GET location → 시청 기록
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
            // 관리자 미리보기 폴링은 GET /location 과 달리 시청자로 집계되면 안 된다 — 여러 번 봐도 watching=false.
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
        // routeBody 는 type/provenance 를 담지 않는다 — 기존 클라이언트 요청과 동일. 저장/조회가 그대로 동작하고
        // 두 필드는 null 로 응답돼야 한다(미전송 시 기존 동작 완전 동일 — additive 계약).
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
        val tooLong: String = "가".repeat(201) // MAX_ROUTE_PROVENANCE_LENGTH(200) 초과
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
        // 보호자 경로 조회를 여러 번 해도
        repeat(3) { mockMvc.get("/share/$token/route").andExpect { status { isOk() } } }
        // 관리자 watching 은 여전히 false(경로 조회는 markViewed 하지 않음).
        mockMvc.post("/share/$token/watching") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"owner_secret\": \"$secret\"}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.watching") { value(false) }
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

    @TestConfiguration
    class ShareTestConfig {
        /** 가변 Clock — 테스트가 주입받아 만료 경계를 전진시킨다. */
        @Bean
        fun clock(): MutableTestClock = MutableTestClock()

        @Bean
        fun locationStore(clock: MutableTestClock): LocationStore = InMemoryLocationStore(clock)

        @Bean
        fun shareService(store: LocationStore, clock: MutableTestClock): ShareService =
            ShareService(store, clock, "http://localhost:8119")
    }
}
