package io.soo.bueongi.core.support.error

import org.springframework.http.HttpStatus

/**
 * 클라이언트에게 그대로 보여줄 응답을 예외가 스스로 안다.
 * [ApiControllerAdvice] 는 종류를 확인하지 않고 이 값들만 옮겨 담는다 — 새 예외를 추가해도 핸들러는 늘지 않는다.
 */
abstract class ApiException(
    val status: HttpStatus,
    val code: String,
    val title: String,
    /** 사용자에게 보이는 문구. 로그용 [message] 와 달리 내부 수치를 담지 않는다. */
    val detail: String,
    val fieldErrors: List<ApiFieldError> = emptyList(),
    message: String,
) : RuntimeException(message)
