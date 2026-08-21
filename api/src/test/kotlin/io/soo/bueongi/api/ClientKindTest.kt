package io.soo.bueongi.api

import io.soo.bueongi.api.config.clientKindOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClientKindTest {

    @Test
    fun `앱이 밝힌 값을 UA 보다 먼저 믿는다`() {
        val kind: String = clientKindOf(
            declaredPlatform = "android-app-emulator",
            userAgent = "Mozilla/5.0 (Linux; Android 14) Chrome/128.0 Mobile Safari/537.36",
        )

        assertThat(kind).isEqualTo("android-app-emulator")
    }

    @Test
    fun `모바일 브라우저를 PC 크롬으로 잘못 읽지 않는다`() {
        val android: String = clientKindOf(
            declaredPlatform = null,
            userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S911N) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36",
        )
        val iphone: String = clientKindOf(
            declaredPlatform = null,
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1 Safari/604.1",
        )

        assertThat(android).isEqualTo("android-web")
        assertThat(iphone).isEqualTo("ios-web")
    }

    @Test
    fun `PC 브라우저와 앱 네트워크 라이브러리를 가려낸다`() {
        val chrome: String = clientKindOf(
            declaredPlatform = null,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/128.0 Safari/537.36",
        )

        assertThat(chrome).isEqualTo("pc-chrome")
        assertThat(clientKindOf(null, "okhttp/4.12.0")).isEqualTo("android-app")
        assertThat(clientKindOf(null, "bueongi/1.0 CFNetwork/1490 Darwin/23.4.0")).isEqualTo("ios-app")
    }

    @Test
    fun `사람이 아닌 호출자도 이름을 갖는다`() {
        assertThat(clientKindOf(null, "curl/8.7.1")).isEqualTo("curl")
        assertThat(clientKindOf(null, "python-requests/2.31.0")).isEqualTo("script")
        assertThat(clientKindOf(null, "")).isEqualTo("unknown")
        assertThat(clientKindOf(null, "SomethingNew/1.0")).isEqualTo("other")
    }
}
