package io.soo.bueongi.core.app

import io.soo.bueongi.core.appinfo.api.controller.HealthController
import io.soo.bueongi.core.appinfo.domain.service.HealthService
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@Tag("context")
@WebMvcTest(HealthController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(HealthControllerTest.Config::class, HealthService::class)
@ActiveProfiles("test")
class HealthControllerTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `200 과 status UP, 서비스 이름, locationStore 를 내려준다`() {
        mockMvc.get("/api/health")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.status") { value("UP") }
                jsonPath("$.service") { value("bueongi-backend") }
                jsonPath("$.locationStore") { value("memory") }
            }
    }

    @TestConfiguration
    class Config {
        @Bean
        fun shareSessionStore(): ShareSessionStore = StubShareSessionStore("memory")
    }
}
