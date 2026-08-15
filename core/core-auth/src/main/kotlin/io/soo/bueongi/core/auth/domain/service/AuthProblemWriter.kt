package io.soo.bueongi.core.auth.domain.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail

object AuthProblemWriter {

    fun write(
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        status: HttpStatus,
        code: String,
        title: String,
        detail: String,
    ) {
        val problem: ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            setProperty("code", code)
        }
        response.status = status.value()
        response.characterEncoding = "UTF-8"
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.use { it.write(objectMapper.writeValueAsString(problem)) }
    }
}
