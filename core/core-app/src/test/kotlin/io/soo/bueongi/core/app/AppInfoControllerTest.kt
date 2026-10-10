package io.soo.bueongi.core.app

import io.soo.bueongi.core.appinfo.api.controller.AppInfoController
import io.soo.bueongi.core.appinfo.domain.service.AppInfoService
import io.soo.bueongi.core.common.event.ApiEventPublisher
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Properties

@Tag("context")
@WebMvcTest(AppInfoController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiEventPublisher::class, AppInfoControllerTest.Config::class, AppInfoService::class)
@ActiveProfiles("test")
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

    @TestConfiguration
    class Config {
        @Bean
        fun buildProperties(): BuildProperties {
            val entries = Properties()
            entries["version"] = "1.2.3"
            return BuildProperties(entries)
        }
    }
}
