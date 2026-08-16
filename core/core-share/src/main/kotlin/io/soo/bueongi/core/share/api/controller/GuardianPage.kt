package io.soo.bueongi.core.share.api.controller

import io.soo.bueongi.support.util.JsEscape
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/** 보호자 페이지 HTML 을 만든다. 토큰은 스크립트 안 문자열 리터럴에 들어가므로 JS 문맥으로 이스케이프한다. */
@Component
class GuardianPage {

    private val template: String by lazy {
        ClassPathResource("share/guardian.html").inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun render(token: String): String {
        val escapedToken: String = JsEscape.escape(token)
        return template.replace(TOKEN_PLACEHOLDER, escapedToken)
    }

    private companion object {
        const val TOKEN_PLACEHOLDER: String = "__SHARE_TOKEN__"
    }
}
