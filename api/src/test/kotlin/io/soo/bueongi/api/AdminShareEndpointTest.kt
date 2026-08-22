package io.soo.bueongi.api

import io.soo.bueongi.core.share.api.controller.ShareAdminController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ShareAdminController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminShareEndpointTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `share-status 는 활성 세션 수를 반환한다`() {
        mockMvc.get("/api/admin/share-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.activeSessions") { value(3) }
            }
    }
    @Test
    fun `share-sessions 는 활성 공유 목록을 계약 형태로 반환한다`() {
        mockMvc.get("/api/admin/share-sessions")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.sessions.length()") { value(3) }
                jsonPath("$.sessions[0].token") { value("tok0") }
                jsonPath("$.sessions[0].shareUrl") { value("http://localhost:3619/share/tok0") }
                jsonPath("$.sessions[0].lat") { value(37.5) }
                jsonPath("$.sessions[0].lng") { value(127.0) }
                jsonPath("$.sessions[0].updatedAtMs") { isNumber() }
                jsonPath("$.sessions[0].ttlSeconds") { value(3600) }
                jsonPath("$.sessions[0].viewerActive") { value(false) }
                jsonPath("$.sessions[1].token") { value("tok1") }
                jsonPath("$.sessions[1].lat") { value(null) }
                jsonPath("$.sessions[1].updatedAtMs") { value(null) }
                jsonPath("$.sessions[0].ownerSecret") { doesNotExist() }
                jsonPath("$.sessions[0].owner_secret") { doesNotExist() }
                jsonPath("$.sessions[0].route") { doesNotExist() }
            }
    }
    @Test
    fun `share-sessions token location 은 200 과 snake_case LocationView 를 반환한다`() {
        mockMvc.get("/api/admin/share-sessions/tok0/location")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.lat") { value(37.5) }
                jsonPath("$.lng") { value(127.0) }
                jsonPath("$.updated_at") { exists() }
                jsonPath("$.expired") { value(false) }
                jsonPath("$.remaining_m") { value(null) }
                jsonPath("$.remaining_s") { value(null) }
                jsonPath("$.updatedAt") { doesNotExist() }
            }
    }
    @Test
    fun `share-sessions token location 반복 열람은 viewerActive 를 올리지 않는다(비집계)`() {
        repeat(3) {
            mockMvc.get("/api/admin/share-sessions/tok0/location").andExpect { status { isOk() } }
        }
        mockMvc.get("/api/admin/share-sessions")
            .andExpect {
                status { isOk() }
                jsonPath("$.sessions[0].token") { value("tok0") }
                jsonPath("$.sessions[0].viewerActive") { value(false) }
            }
    }
    @Test
    fun `없는 token 의 share-sessions location 은 200 expired=true(공개 조회와 동일 규약)`() {
        mockMvc.get("/api/admin/share-sessions/does-not-exist/location")
            .andExpect {
                status { isOk() }
                jsonPath("$.expired") { value(true) }
                jsonPath("$.lat") { value(null) }
            }
    }

    /**
     * 관리자 미리보기는 보호자 화면을 재사용하는데, 경로를 보호자 엔드포인트로 받으면
     * 공유 서버 base URL 이 필요해 목적지·경로선·잔여 표기가 조용히 빠졌다. 같은 오리진에서 받는다.
     */
    @Test
    fun `share-sessions token route 는 등록된 경로를 보호자와 같은 와이어로 반환한다`() {
        mockMvc.get("/api/admin/share-sessions/tok0/route")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.expired") { value(false) }
                jsonPath("$.route.destination.name") { value("삼성역") }
                jsonPath("$.route.path.length()") { value(2) }
                jsonPath("$.route.total_distance_m") { value(1200) }
                jsonPath("$.route.total_time_s") { value(900) }
                jsonPath("$.route.type") { value("safe") }
                jsonPath("$.route.totalDistanceM") { doesNotExist() }
            }
    }

    @Test
    fun `경로 미등록 세션의 route 는 200 route=null 이다`() {
        mockMvc.get("/api/admin/share-sessions/tok1/route")
            .andExpect {
                status { isOk() }
                jsonPath("$.expired") { value(false) }
                jsonPath("$.route") { value(null) }
            }
    }

    @Test
    fun `share-sessions token route 반복 열람은 viewerActive 를 올리지 않는다(비집계)`() {
        repeat(3) {
            mockMvc.get("/api/admin/share-sessions/tok0/route").andExpect { status { isOk() } }
        }
        mockMvc.get("/api/admin/share-sessions")
            .andExpect {
                status { isOk() }
                jsonPath("$.sessions[0].token") { value("tok0") }
                jsonPath("$.sessions[0].viewerActive") { value(false) }
            }
    }

    @Test
    fun `없는 token 의 route 는 200 expired=true(공개 조회와 동일 규약)`() {
        mockMvc.get("/api/admin/share-sessions/does-not-exist/route")
            .andExpect {
                status { isOk() }
                jsonPath("$.expired") { value(true) }
                jsonPath("$.route") { value(null) }
            }
    }
}
