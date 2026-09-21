package io.soo.bueongi.support.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CauseChainTest {
    @Test
    fun `겉 예외 뒤에 숨은 원인까지 사슬로 적는다`() {
        val root = IllegalArgumentException("Illegal unquoted character ((CTRL-CHAR, code 0))")
        val middle = RuntimeException("JSON parse error", root)
        val outer = IllegalStateException("Error while extracting response", middle)

        val chain: String = causeChainOf(outer)

        assertThat(chain).contains("IllegalStateException: Error while extracting response")
        assertThat(chain).contains("RuntimeException: JSON parse error")
        assertThat(chain).contains("IllegalArgumentException: Illegal unquoted character")
        assertThat(chain.indexOf("IllegalStateException")).isLessThan(chain.indexOf("IllegalArgumentException"))
    }

    private class SelfCaused : RuntimeException("반복") {
        override val cause: Throwable
            get() = this
    }

    @Test
    fun `사슬이 스스로를 가리켜도 멈춘다`() {
        assertThat(causeChainOf(SelfCaused())).isEqualTo("SelfCaused: 반복")
    }

    @Test
    fun `예외 메시지에 실려 온 URL 의 질의 문자열은 통째로 지운다`() {
        val error =
            RuntimeException(
                """I/O error on GET request for "https://apis.data.go.kr/1741000/x?serviceKey=SECRET-KEY&pageNo=3"""",
            )

        val chain: String = causeChainOf(error)

        assertThat(chain).doesNotContain("SECRET-KEY")
        assertThat(chain).doesNotContain("pageNo=3")
        assertThat(chain).contains("https://apis.data.go.kr/1741000/x?***")
    }

    @Test
    fun `아는 키 값은 URL 밖에 있어도 지운다`() {
        val error = RuntimeException("form={esntlId=ID-1234, authKey=AUTH-9999, pageIndex=1}")

        val chain: String = causeChainOf(error, "ID-1234", "AUTH-9999")

        assertThat(chain).doesNotContain("ID-1234")
        assertThat(chain).doesNotContain("AUTH-9999")
        assertThat(chain).contains("pageIndex=1")
    }

    @Test
    fun `인증 파라미터는 값을 몰라도 이름만으로 지운다`() {
        val masked: String = maskSecrets("body: serviceKey=RAW-KEY&appKey=OTHER-KEY&numOfRows=100")

        assertThat(masked).doesNotContain("RAW-KEY")
        assertThat(masked).doesNotContain("OTHER-KEY")
        assertThat(masked).contains("numOfRows=100")
    }

    // Kakao 는 401 본문에 키를 괄호로 담아 돌려준다(실측).
    @Test
    fun `원천이 되돌려 준 응답 본문 속 인증키도 지운다`() {
        val body = """401 Unauthorized: {"message":"appKey(0000000000000000000000000000dead) does not exist"}"""

        val masked: String = maskSecrets(body)

        assertThat(masked).doesNotContain("0000000000000000000000000000dead")
        assertThat(masked).contains("appKey(***)")
        assertThat(masked).contains("401 Unauthorized")
    }

    @Test
    fun `이름이 비슷하다고 지우지는 않는다`() {
        assertThat(maskSecrets("monkey=바나나")).isEqualTo("monkey=바나나")
    }

    @Test
    fun `아주 긴 메시지는 잘라서 로그를 덮지 않게 한다`() {
        val chain: String = causeChainOf(RuntimeException("가".repeat(1000)))

        assertThat(chain.length).isLessThan(300)
        assertThat(chain).endsWith("…")
    }
}
