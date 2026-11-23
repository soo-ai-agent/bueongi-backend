package io.soo.bueongi.core.app

import io.soo.bueongi.core.admin.ops.api.controller.SafetySpotFetchStatusController
import io.soo.bueongi.core.admin.ops.domain.externalfetch.SafetySpotFetchLedger
import io.soo.bueongi.core.admin.ops.domain.externalfetch.SafetySpotFetchStatusService
import io.soo.bueongi.core.common.event.SafetySpotFetchPerformed
import io.soo.bueongi.core.enums.SafetySpotType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@Tag("context")
@WebMvcTest(SafetySpotFetchStatusController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SafetySpotFetchLedger::class, SafetySpotFetchStatusService::class)
@ActiveProfiles("test")
class AdminSafetySpotFetchEndpointTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val ledger: SafetySpotFetchLedger,
) {
    @Test
    fun `종류별 외부 수집 집계를 내려준다`() {
        ledger.record(SafetySpotFetchPerformed(SafetySpotType.SECURITY_LAMP, "3860000", receivedCount = 120))
        ledger.record(SafetySpotFetchPerformed(SafetySpotType.SECURITY_LAMP, "5410000", receivedCount = null))

        mockMvc.get("/api/admin/safety-spot-fetches")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].type") { value("security_lamp") }
                jsonPath("$[0].successCount") { value(1) }
                jsonPath("$[0].failureCount") { value(1) }
                jsonPath("$[0].lastScope") { value("5410000") }
                jsonPath("$[0].lastReceivedCount") { value(null) }
            }
    }
}
