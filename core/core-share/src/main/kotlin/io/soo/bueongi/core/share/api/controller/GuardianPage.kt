package io.soo.bueongi.core.share.api.controller

import io.soo.bueongi.support.util.escapeForJs
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

// 토큰이 스크립트 안 문자열 리터럴에 들어가므로 JS 문맥으로 이스케이프한다.
@Component
class GuardianPage {
    private val template: String by lazy {
        ClassPathResource("share/guardian.html").inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun render(token: String): String {
        val escapedToken: String = escapeForJs(token)
        return template.replace("__SHARE_TOKEN__", escapedToken)
    }
}
