package io.soo.bueongi.api

import io.soo.bueongi.api.config.normalizeRequestPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequestPathNormalizerTest {
    @Test
    fun `share 토큰과 하위 액션의 토큰 세그먼트를 치환한다`() {
        assertThat(normalizeRequestPath("/share/abc123")).isEqualTo("/share/{token}")
        assertThat(normalizeRequestPath("/share/abc123/location")).isEqualTo("/share/{token}/location")
        assertThat(normalizeRequestPath("/share/abc123/end")).isEqualTo("/share/{token}/end")
        assertThat(normalizeRequestPath("/share/abc123/watching")).isEqualTo("/share/{token}/watching")
        assertThat(normalizeRequestPath("/share/abc123/route")).isEqualTo("/share/{token}/route")
    }

    @Test
    fun `share create 는 토큰이 아니라 그대로 둔다`() {
        assertThat(normalizeRequestPath("/share/create")).isEqualTo("/share/create")
    }

    @Test
    fun `api 경로와 알 수 없는 경로는 원경로를 보존한다`() {
        assertThat(normalizeRequestPath("/api/places/search")).isEqualTo("/api/places/search")
        assertThat(normalizeRequestPath("/api/routes/safe-compare")).isEqualTo("/api/routes/safe-compare")
        assertThat(normalizeRequestPath("/api/routes/safe-compare")).isEqualTo("/api/routes/safe-compare")
        assertThat(normalizeRequestPath("/api/safety-spots")).isEqualTo("/api/safety-spots")
        assertThat(normalizeRequestPath("/something/else")).isEqualTo("/something/else")
    }

    @Test
    fun `share 루트만 있으면 치환 대상이 없어 그대로 둔다`() {
        assertThat(normalizeRequestPath("/share")).isEqualTo("/share")
    }
}
