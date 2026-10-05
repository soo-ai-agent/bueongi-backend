package io.soo.bueongi.core.admin.auth.domain.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.common.error.ApiException
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType

// 인증 필터는 컨트롤러보다 먼저 돌아 ApiControllerAdvice 가 예외를 받지 못한다 — 같은 본문을 응답에 직접 쓴다.
fun writeAuthProblem(response: HttpServletResponse, objectMapper: ObjectMapper, exception: ApiException) {
    response.status = exception.status.value()
    response.characterEncoding = "UTF-8"
    response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    response.writer.use { it.write(objectMapper.writeValueAsString(exception.toProblemDetail())) }
}
