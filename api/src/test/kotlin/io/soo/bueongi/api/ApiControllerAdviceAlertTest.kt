package io.soo.bueongi.api


import io.soo.bueongi.core.common.event.ShareStoreUnavailable
import io.soo.bueongi.api.ApiEventPublisher
import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import io.soo.bueongi.api.ApiControllerAdvice

import io.soo.bueongi.storage.db.core.share.store.LocationStoreUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

class ApiControllerAdviceAlertTest {

    private class RecordingPublisher : ApplicationEventPublisher {
        val events: MutableList<Any> = ArrayList()
        override fun publishEvent(event: Any) {
            events.add(event)
        }
    }

    @Test
    fun `미처리 예외(500)면 예외 클래스별 key 로 알림을 보낸다 — 응답 본문 비노출 계약은 유지`() {
        val notifier: RecordingPublisher = RecordingPublisher()
        val advice: ApiControllerAdvice = ApiControllerAdvice(ApiEventPublisher(notifier))

        val body = advice.handleUncaught(IllegalStateException("내부 상태 오류"))

        assertThat(notifier.events).hasSize(1)
        val event: OperationEvent = notifier.events[0] as OperationEvent
        val key: String = event.key
        val title: String = event.title
        val detail: String = event.detail
        assertThat(key).isEqualTo("api-500:IllegalStateException")
        assertThat(title).contains("500")
        assertThat(detail).contains("IllegalStateException").contains("내부 상태 오류")
        assertThat(body.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
        assertThat(body.properties?.get("code")).isEqualTo("INTERNAL_ERROR")
        assertThat(body.detail).doesNotContain("내부 상태 오류")
    }

    @Test
    fun `저장소 장애(503)면 share-store-unavailable key 로 알림 + Retry-After 를 주고 원인은 비노출한다`() {
        val notifier: RecordingPublisher = RecordingPublisher()
        val advice: ApiControllerAdvice = ApiControllerAdvice(ApiEventPublisher(notifier))

        val response = advice.handleShareStoreUnavailable(
            LocationStoreUnavailableException("findByToken", RuntimeException("redis down")),
        )

        assertThat(notifier.events).hasSize(1)
        assertThat(notifier.events[0]).isInstanceOf(ShareStoreUnavailable::class.java)
        assertThat(response.statusCode.value()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value())
        assertThat(response.body?.properties?.get("code")).isEqualTo("SHARE_STORE_UNAVAILABLE")
        assertThat(response.headers.getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5")
        assertThat(response.body?.detail).doesNotContain("redis down")
    }

    @Test
    fun `알림기가 없어도(null) 500 처리는 그대로 동작한다`() {
        val advice: ApiControllerAdvice = ApiControllerAdvice()

        assertThatCode {
            val body = advice.handleUncaught(RuntimeException("x"))
            assertThat(body.properties?.get("code")).isEqualTo("INTERNAL_ERROR")
        }.doesNotThrowAnyException()
    }
}
