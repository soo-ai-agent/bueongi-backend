package io.soo.bueongi.core.app

import io.soo.bueongi.core.admin.ops.api.controller.AlertController
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(AlertController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
@Tag("context")
class AdminAlertEndpointTest(@param:Autowired private val mockMvc: MockMvc) {
    @Test
    fun `alerts 는 최근 장애 이력을 배열로 반환한다`() {
        mockMvc.get("/api/admin/alerts")
            .andExpect {
                status { isOk() }
                jsonPath("$.alerts[0].key") { value("tmap-route-fallback") }
                jsonPath("$.alerts[0].title") { value("Tmap 경로 API 실패") }
                jsonPath("$.alerts[0].atEpochMs") { isNumber() }
            }
    }
}
