package io.soo.bueongi.api

import io.soo.bueongi.api.ApiControllerAdvice

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(BoomController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiControllerAdvice::class)
class UncaughtExceptionHandlerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `미처리 예외는 500 과 INTERNAL_ERROR 코드를 반환하고 내부 details 를 노출하지 않는다`() {
        mockMvc.get("/test/boom").andExpect {
            status { isInternalServerError() }
            jsonPath("$.title") { value("Internal Server Error") }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
            jsonPath("$.status") { value(500) }
            jsonPath("$.detail") { value("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.") }
        }
    }
}

@RestController
private class BoomController {
    @GetMapping("/test/boom")
    fun boom(): Nothing = throw IllegalStateException("내부 전용 메시지 - 응답에 노출되면 안 됨")
}
