package io.soo.bueongi.api

import io.soo.bueongi.core.ops.api.controller.AlertController

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@WebMvcTest(AlertController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SlackConfigControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `GET 은 미설정 상태와 5종 카테고리를 반환한다`() {
        mockMvc.get("/api/admin/slack-config")
            .andExpect {
                status { isOk() }
                jsonPath("$.webhookConfigured") { value(false) }
                jsonPath("$.webhookMasked") { value(null) }
                jsonPath("$.enabled") { value(false) }
                jsonPath("$.throttleMinutes") { value(10) }
                jsonPath("$.categories.length()") { value(5) }
                jsonPath("$.categories[0].key") { value("api-500") }
                jsonPath("$.categories[0].label") { value("서버 오류(미처리 500)") }
                jsonPath("$.categories[0].muted") { value(false) }
            }
    }

    @Test
    fun `PUT 은 편집값을 반영한 요약을 반환한다`() {
        mockMvc.put("/api/admin/slack-config") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":true,"throttleMinutes":30,"mutedCategories":["api-500","tmap-route-fallback"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.enabled") { value(true) }
            jsonPath("$.throttleMinutes") { value(30) }
            jsonPath("$.categories[0].key") { value("api-500") }
            jsonPath("$.categories[0].muted") { value(true) }
            jsonPath("$.categories[1].key") { value("kakao-place-fallback") }
            jsonPath("$.categories[1].muted") { value(false) }
            jsonPath("$.categories[2].key") { value("tmap-route-fallback") }
            jsonPath("$.categories[2].muted") { value(true) }
        }
    }

    @Test
    fun `PUT 후 GET 하면 변경이 반영되어 있다`() {
        mockMvc.put("/api/admin/slack-config") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":true,"throttleMinutes":45,"mutedCategories":["safetySpot-refresh"]}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/admin/slack-config")
            .andExpect {
                status { isOk() }
                jsonPath("$.enabled") { value(true) }
                jsonPath("$.throttleMinutes") { value(45) }
                jsonPath("$.categories[4].key") { value("safetySpot-refresh") }
                jsonPath("$.categories[4].muted") { value(true) }
            }
    }

    @Test
    fun `PUT throttleMinutes 가 범위를 벗어나면 400 VALIDATION_FAILED`() {
        mockMvc.put("/api/admin/slack-config") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":true,"throttleMinutes":0,"mutedCategories":[]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `PUT 알 수 없는 카테고리는 422 INVALID_SLACK_CATEGORY`() {
        mockMvc.put("/api/admin/slack-config") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":true,"throttleMinutes":10,"mutedCategories":["nope"]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_SLACK_CATEGORY") }
            jsonPath("$.errors[0].field") { value("mutedCategories") }
        }
    }

    @Test
    fun `POST test 는 발송 결과 shape 을 반환한다(미설정이라 sent false)`() {
        mockMvc.post("/api/admin/slack-config/test")
            .andExpect {
                status { isOk() }
                jsonPath("$.sent") { value(false) }
                jsonPath("$.enabled") { value(false) }
            }
    }
}
