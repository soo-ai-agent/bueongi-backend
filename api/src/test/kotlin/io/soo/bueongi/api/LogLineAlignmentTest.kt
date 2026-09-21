package io.soo.bueongi.api

import io.soo.bueongi.api.config.ExternalCallLogLine
import io.soo.bueongi.api.config.RequestLogLine
import io.soo.bueongi.core.enums.RequestCaller
import io.soo.bueongi.support.logging.LOG_COLUMN_SEPARATOR
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogLineAlignmentTest {

    private fun inbound(path: String): String = RequestLogLine(
        method = "GET", path = path, status = 200, durationMs = 12,
        clientId = null, address = "127.0.0.1", kind = "curl",
        caller = RequestCaller.USER,
    ).format()

    private fun outbound(endpoint: String): String = ExternalCallLogLine(
        provider = "kakao", method = "GET", endpoint = endpoint, status = 200, durationMs = 12,
    ).format()

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

    @Test
    fun `수신 줄과 송신 줄의 다음 칸이 같은 열에서 시작한다`() {
        assertThat(columnAfterTarget(inbound("/api/places/search")))
            .isEqualTo(columnAfterTarget(outbound("/v2/local/search/keyword.json")))
    }

    @Test
    fun `줄 끝에 채운 공백이 남지 않는다`() {
        assertThat(outbound("/x")).isEqualTo(outbound("/x").trimEnd())
    }
}
