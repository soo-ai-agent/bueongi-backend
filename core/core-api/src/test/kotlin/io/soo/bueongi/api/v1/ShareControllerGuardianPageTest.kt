package io.soo.bueongi.api.v1

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get

/**
 * GET /share/{token} 보호자 페이지(HTML)의 계약과 reflected-XSS/경로주입 방어(escapeJs 서버측 +
 * encodeURIComponent 클라이언트측)를 고정한다.
 */
class ShareControllerGuardianPageTest : ShareControllerTestSupport() {

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
}
