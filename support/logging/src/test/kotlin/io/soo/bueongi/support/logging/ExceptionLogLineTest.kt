package io.soo.bueongi.support.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ExceptionLogLineTest {
    @AfterEach
    fun clearTrace() {
        endTrace()
    }

    private fun line(kind: String = IGNORED_EXCEPTION): ExceptionLogLine =
        ExceptionLogLine(
            kind = kind,
            counterpart = "data_go_kr",
            subject = "/1741000/StanReginCd",
            cause = "HttpClientErrorException: 401 Unauthorized",
            action = "갱신 중단(기존 데이터 유지)",
            paging = "page=3 시도=2회",
        )

    @Test
    fun `예외 줄은 방향 표식으로 시작해 수신 송신과 갈린다`() {
        val formatted: String = line().format()

        assertThat(formatted).startsWith("[예외] 무시")
        assertThat(formatted).doesNotContain(INBOUND_DIRECTION)
        assertThat(formatted).doesNotContain(OUTBOUND_DIRECTION)
    }

    @Test
    fun `상대 결과 대상 순으로 적고 원인과 조치를 표식으로 가른다`() {
        val columns: List<String> = line().format().split(LOG_COLUMN_SEPARATOR).map { it.trim() }

        assertThat(columns[1]).isEqualTo("data_go_kr")
        assertThat(columns[2]).isEqualTo(NO_RESPONSE_STATUS)
        assertThat(columns[3]).isEqualTo("/1741000/StanReginCd")
        assertThat(columns[4]).isEqualTo("📄 page=3 시도=2회")
        assertThat(columns[5]).isEqualTo("✖ HttpClientErrorException: 401 Unauthorized")
        assertThat(columns[6]).isEqualTo("↳ 갱신 중단(기존 데이터 유지)")
    }

    @Test
    fun `앞의 세 칸은 값이 짧아도 폭을 채운다`() {
        val short: String =
            ExceptionLogLine(
                kind = IGNORED_EXCEPTION,
                counterpart = "tmap",
                subject = "/tmap/x",
                cause = "RuntimeException",
            ).format()
        val long: String = line().format()

        assertThat(short.indexOf("/tmap/x")).isEqualTo(long.indexOf("/1741000"))
    }

    @Test
    fun `요청에서 갈라진 줄에는 짝 표식이 붙고 요청 밖에서는 붙지 않는다`() {
        val outsideRequest: String = line().format()
        beginTrace("POST /api/routes")
        val insideRequest: String = line().format()

        assertThat(outsideRequest).doesNotContain("🔗")
        assertThat(insideRequest).contains("🔗 ${currentTraceId()}")
    }

    @Test
    fun `표식은 요청이 끝나면 지워진다`() {
        beginTrace("POST /api/routes")
        endTrace()

        assertThat(currentTraceId()).isEmpty()
        assertThat(currentRequestOrigin()).isEmpty()
    }
}
