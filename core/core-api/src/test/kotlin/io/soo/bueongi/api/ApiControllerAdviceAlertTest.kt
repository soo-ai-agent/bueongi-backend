package io.soo.bueongi.api

import io.soo.bueongi.storage.db.core.share.LocationStoreUnavailableException
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/**
 * [ApiControllerAdvice] 의 운영 알림 배선 테스트 — 처리되지 않은 예외(500)에서만 알림이 나가고,
 * 알림 유무와 무관하게 응답 계약(코드·비노출)은 그대로임을 고정한다.
 */
class ApiControllerAdviceAlertTest {

    private class RecordingNotifier : AlertNotifier {
        val notified = ArrayList<Triple<String, String, String>>()
        override val isEnabled = true
        override fun notify(key: String, title: String, detail: String) {
            notified.add(Triple(key, title, detail))
        }
    }

    @Test
    fun `미처리 예외(500)면 예외 클래스별 key 로 알림을 보낸다 — 응답 본문 비노출 계약은 유지`() {
        val notifier = RecordingNotifier()
        val advice = ApiControllerAdvice(notifier)

        val body = advice.handleUncaught(IllegalStateException("내부 상태 오류"))

        assertThat(notifier.notified).hasSize(1)
        val (key, title, detail) = notifier.notified[0]
        assertThat(key).isEqualTo("api-500:IllegalStateException")
        assertThat(title).contains("500")
        assertThat(detail).contains("IllegalStateException").contains("내부 상태 오류")
        // 응답 본문은 기존 계약 그대로(내부 메시지 비노출).
        assertThat(body.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
        assertThat(body.properties?.get("code")).isEqualTo("INTERNAL_ERROR")
        assertThat(body.detail).doesNotContain("내부 상태 오류")
    }

    @Test
    fun `저장소 장애(503)면 share-store-unavailable key 로 알림 + Retry-After 를 주고 원인은 비노출한다`() {
        val notifier = RecordingNotifier()
        val advice = ApiControllerAdvice(notifier)

        val response = advice.handleShareStoreUnavailable(
            LocationStoreUnavailableException("findByToken", RuntimeException("redis down")),
        )

        assertThat(notifier.notified).hasSize(1)
        assertThat(notifier.notified[0].first).isEqualTo("share-store-unavailable")
        // 만료(404)와 구분되는 503 + 안정 코드 + 재시도 힌트.
        assertThat(response.statusCode.value()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value())
        assertThat(response.body?.properties?.get("code")).isEqualTo("SHARE_STORE_UNAVAILABLE")
        assertThat(response.headers.getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5")
        // 내부 원인(예외 메시지)은 응답 본문에 노출하지 않는다.
        assertThat(response.body?.detail).doesNotContain("redis down")
    }

    @Test
    fun `알림기가 없어도(null) 500 처리는 그대로 동작한다`() {
        val advice = ApiControllerAdvice()

        assertThatCode {
            val body = advice.handleUncaught(RuntimeException("x"))
            assertThat(body.properties?.get("code")).isEqualTo("INTERNAL_ERROR")
        }.doesNotThrowAnyException()
    }
}
