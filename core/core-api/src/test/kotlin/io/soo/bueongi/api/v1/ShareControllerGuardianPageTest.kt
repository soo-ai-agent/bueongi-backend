package io.soo.bueongi.api.v1

import io.soo.bueongi.share.*

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get

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
        val malicious: String = "a\"b<>"
        mockMvc.get("/share/{token}", malicious).andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString("var TOKEN = \"a\\\"b\\u003c\\u003e\";")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("var TOKEN = \"a\"b"))) }
        }
    }

    @Test
    fun `보호자 페이지는 폴링 URL 에 토큰을 encodeURIComponent 로 감싸 경로주입을 막는다`() {
        val token: String = createShare()
        mockMvc.get("/share/$token").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString("encodeURIComponent(TOKEN)")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("'/share/' + TOKEN +"))) }
        }
    }
}
