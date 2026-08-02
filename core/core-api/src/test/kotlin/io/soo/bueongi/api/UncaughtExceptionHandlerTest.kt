package io.soo.bueongi.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * [ApiControllerAdvice] 의 catch-all(500) 핸들러 슬라이스 테스트.
 *
 * 실제 컨트롤러는 예외를 던지지 않으므로(도메인 예외는 모두 전용 핸들러가 처리), 미처리 런타임 예외를
 * 의도적으로 던지는 테스트 전용 컨트롤러([BoomController])를 띄워 500 응답이 표준 스키마+`code` 를
 * 따르는지, 그리고 응답 본문에 내부 details(예외 메시지)가 새지 않는지 고정한다.
 */
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

/** 미처리 예외 경로를 재현하기 위한 테스트 전용 컨트롤러. 프로덕션 빈으로 등록되지 않는다. */
@RestController
private class BoomController {
    @GetMapping("/test/boom")
    fun boom(): Nothing = throw IllegalStateException("내부 전용 메시지 - 응답에 노출되면 안 됨")
}
