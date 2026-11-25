package io.soo.bueongi.core.app

import io.soo.bueongi.core.appinfo.api.controller.AppInfoController
import io.soo.bueongi.core.appinfo.domain.service.AppInfoService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@Tag("context")
@WebMvcTest(AppInfoController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AppInfoControllerTestConfig::class, AppInfoService::class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "bueongi.app.min-supported-version=1.1.0",
        "bueongi.app.latest-version=1.4.2",
    ],
)
class AppInfoControllerTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `빌드 정보의 서버 버전을 내려준다`() {
        mockMvc.get("/api/app-info")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.serverVersion") { value("1.2.3") }
            }
    }

    @Test
    fun `앱 업데이트 기준 버전을 설정값 그대로 내려준다`() {
        mockMvc.get("/api/app-info")
            .andExpect {
                status { isOk() }
                jsonPath("$.minSupportedAppVersion") { value("1.1.0") }
                jsonPath("$.latestAppVersion") { value("1.4.2") }
            }
    }
}
