package io.soo.bueongi.api.auth

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail

/**
 * 인증/인가 실패(401·403)를 bueongi 표준 에러 포맷(RFC 7807 [ProblemDetail] + `code` 프로퍼티)으로 응답한다.
 * `ApiControllerAdvice` 의 `application/problem+json` 관례와 동일하게 맞춘다.
 */
object AuthProblemWriter {

    fun write(
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        status: HttpStatus,
        code: String,
        title: String,
        detail: String,
    ) {
        val problem = ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            setProperty("code", code)
        }
        response.status = status.value()
        response.characterEncoding = "UTF-8"
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.use { it.write(objectMapper.writeValueAsString(problem)) }
    }
}
