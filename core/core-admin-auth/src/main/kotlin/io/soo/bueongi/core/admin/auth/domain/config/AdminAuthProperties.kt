package io.soo.bueongi.core.admin.auth.domain.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bueongi.admin-auth")
data class AdminAuthProperties(
    val loginId: String,
    val passwordHash: String,
    val email: String = "",
    val accessTokenSeconds: Long = 900,
    val refreshTokenSeconds: Long = 604800,
) {
    init {
        require(loginId.isNotBlank()) { "로그인 아이디가 비어 있습니다." }
        require(BCRYPT_PATTERN.matches(passwordHash)) { "비밀번호는 cost 10 이상의 bcrypt 해시여야 합니다." }
        require(accessTokenSeconds > 0) { "접근 토큰 수명은 0보다 커야 합니다." }
        require(refreshTokenSeconds > accessTokenSeconds) { "재발급 토큰 수명은 접근 토큰보다 길어야 합니다." }
    }

    private companion object {
        val BCRYPT_PATTERN = Regex("^\\$2[aby]\\$(1\\d|2\\d|3[01])\\$[./A-Za-z0-9]{53}$")
    }
}
