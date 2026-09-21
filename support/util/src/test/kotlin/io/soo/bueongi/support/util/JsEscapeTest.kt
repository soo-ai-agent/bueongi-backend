package io.soo.bueongi.support.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsEscapeTest {
    @Test
    fun `따옴표는 리터럴을 닫지 못하게 탈출된다`() {
        assertThat(escapeForJs("""a"b""")).isEqualTo("""a\"b""")
    }

    @Test
    fun `역슬래시가 먼저 escape 되어 뒤 따옴표 탈출을 무력화하지 못한다`() {
        assertThat(escapeForJs("""a\"b""")).isEqualTo("""a\\\"b""")
    }

    @Test
    fun `꺾쇠는 유니코드로 바뀌어 script 태그를 닫지 못한다`() {
        assertThat(escapeForJs("</script>")).isEqualTo("\\u003c/script\\u003e")
    }

    @Test
    fun `평범한 토큰은 그대로 둔다`() {
        assertThat(escapeForJs("abc-123_XYZ")).isEqualTo("abc-123_XYZ")
    }
}
