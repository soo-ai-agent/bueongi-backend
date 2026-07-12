package io.soo.bueongi.api.admin

import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * [AdminController] api-logs 슬라이스 테스트(@WebMvcTest). 보안 필터는 끄고(실 게이트는 AdminAuthGateTest 가 커버)
 * GET /api/admin/api-logs 의 HTTP 계약(200 + 접속 내역 JSON shape, provider/limit 파라미터 위임)만 고정한다.
 */
@WebMvcTest(AdminController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiLogsControllerTest.FakeStoreConfig::class)
class ApiLogsControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `접속 내역을 200 과 배열로 반환한다`() {
        mockMvc.get("/api/admin/api-logs")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$[0].provider") { value("KAKAO") }
                jsonPath("$[0].endpoint") { value("search") }
                jsonPath("$[0].success") { value(true) }
                jsonPath("$[0].durationMs") { isNumber() }
            }
    }

    @Test
    fun `limit 은 상한(500)으로 절삭되어 저장소에 전달된다`() {
        mockMvc.get("/api/admin/api-logs") { param("limit", "9999") }
            .andExpect { status { isOk() } }
        // FakeStore 가 전달받은 limit 을 기록 — coerceIn(1,500) 결과 검증.
        org.assertj.core.api.Assertions.assertThat(FakeStoreConfig.lastLimit).isEqualTo(500)
    }

    @TestConfiguration
    class FakeStoreConfig {
        @Bean
        fun apiCallLogStore(): ApiCallLogStore = object : ApiCallLogStore {
            override fun record(log: ApiCallLog) = Unit
            override fun findRecent(provider: String?, limit: Int): List<ApiCallLog> {
                lastLimit = limit
                return listOf(
                    ApiCallLog(atEpochMs = 1L, provider = "KAKAO", endpoint = "search", success = true, durationMs = 12),
                )
            }
        }

        companion object {
            var lastLimit: Int = -1
        }
    }
}
