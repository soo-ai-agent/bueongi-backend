package io.soo.bueongi.core.share.api.controller

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.enums.AlertCategory
import io.soo.bueongi.storage.db.share.store.LocationStoreUnavailableException
import io.soo.bueongi.support.logging.currentRequestOrigin
import io.soo.bueongi.support.logging.traceColumn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// 공통 advice 의 Exception 포괄 핸들러보다 먼저 평가되어야 503 계약이 유지된다.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ShareStoreAdvice(
    private val events: ApplicationEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(ShareStoreAdvice::class.java)

    @ExceptionHandler(LocationStoreUnavailableException::class)
    fun handleUnavailable(ex: LocationStoreUnavailableException): ResponseEntity<ProblemDetail> {
        val origin: String = currentRequestOrigin().ifEmpty { UNKNOWN_ORIGIN }
        // 스택에는 외부 호출 URL(인증키 포함)이 섞여 온다 — 예외 종류까지만 남긴다.
        log.error(
            "미처리 예외 | 공유 저장소 | ${HttpStatus.SERVICE_UNAVAILABLE.value()} | $origin | ${traceColumn()} | " +
                "원인=${ex.javaClass.simpleName} | 조치=503 SHARE_STORE_UNAVAILABLE 응답(Retry-After ${RETRY_AFTER_SECONDS}s)",
        )
        events.publishEvent(
            OperationEvent(
                key = AlertCategory.SHARE_STORE_UNAVAILABLE.key,
                title = "위치 공유 저장소(Redis) 접근 실패",
                detail = "${ex.cause?.javaClass?.simpleName ?: "unknown"} — 위치 공유 읽기/쓰기가 일시 중단됩니다.",
            ),
        )
        val body: ProblemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "일시적으로 위치 공유 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
            ).apply {
                title = "Service Unavailable"
                setProperty("code", "SHARE_STORE_UNAVAILABLE")
            }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS.toString())
            .body(body)
    }

    private companion object {
        const val RETRY_AFTER_SECONDS: Int = 5

        const val UNKNOWN_ORIGIN: String = "요청 미상"
    }
}
