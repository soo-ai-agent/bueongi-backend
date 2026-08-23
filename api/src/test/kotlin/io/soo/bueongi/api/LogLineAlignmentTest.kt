package io.soo.bueongi.api

import io.soo.bueongi.api.config.ExternalCallLogLine
import io.soo.bueongi.api.config.RequestLogLine
import io.soo.bueongi.core.enums.RequestCaller
import io.soo.bueongi.support.logging.LOG_COLUMN_SEPARATOR
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 로그를 눈으로 훑을 때 칸이 세로로 맞아야 한다. 경로 길이가 줄마다 달라도
 * **그 뒤 칸(소요시간·표식)이 같은 열에서 시작해야** 한다 — 안 그러면 줄마다 들쭉날쭉해진다.
 */
class LogLineAlignmentTest {

    private fun inbound(path: String): String = RequestLogLine(
        method = "GET", path = path, status = 200, durationMs = 12,
        clientId = null, address = "127.0.0.1", kind = "curl",
        caller = RequestCaller.USER,
    ).format()

    private fun outbound(endpoint: String): String = ExternalCallLogLine(
        provider = "kakao", method = "GET", endpoint = endpoint, status = 200, durationMs = 12,
    ).format()

    /**
     * 소요시간 칸이 시작하는 열. **글자 수가 아니라 터미널 표시 폭**으로 잰다 —
     * 한글은 두 칸을 차지해서, 글자 수로 재면 한글이 든 줄만 다르게 나온다.
     */
    private fun columnAfterTarget(line: String): Int {
        val parts: List<String> = line.split(LOG_COLUMN_SEPARATOR)
        return parts.take(4).sumOf { displayWidthOf(it) + LOG_COLUMN_SEPARATOR.length }
    }

    private fun displayWidthOf(text: String): Int {
        var width = 0
        for (character in text) {
            width += if (character.code in 0xAC00..0xD7A3) 2 else 1
        }
        return width
    }

    @Test
    fun `수신 줄은 경로 길이가 달라도 다음 칸이 같은 열에서 시작한다`() {
        val short: Int = columnAfterTarget(inbound("/wp-login.php"))
        val long: Int = columnAfterTarget(inbound("/api/admin/safety-spot-status/regions"))

        assertThat(short).isEqualTo(long)
    }

    @Test
    fun `송신 줄도 경로 길이가 달라도 다음 칸이 같은 열에서 시작한다`() {
        val short: Int = columnAfterTarget(outbound("/x"))
        val long: Int = columnAfterTarget(outbound("/v2/local/search/keyword.json"))

        assertThat(short).isEqualTo(long)
    }

    /** 수신과 송신이 서로도 맞아야 두 종류를 함께 훑을 수 있다. */
    @Test
    fun `수신 줄과 송신 줄의 다음 칸이 같은 열에서 시작한다`() {
        assertThat(columnAfterTarget(inbound("/api/places/search")))
            .isEqualTo(columnAfterTarget(outbound("/v2/local/search/keyword.json")))
    }

    /** 채운 공백이 줄 끝에 남으면 로그 파일이 지저분해진다. */
    @Test
    fun `줄 끝에 채운 공백이 남지 않는다`() {
        assertThat(outbound("/x")).isEqualTo(outbound("/x").trimEnd())
    }
}
