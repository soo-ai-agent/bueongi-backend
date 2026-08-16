package io.soo.bueongi.api

import io.soo.bueongi.core.place.domain.model.PlaceItem
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import io.soo.bueongi.core.place.domain.service.RemotePlaceProvider
import io.soo.bueongi.core.place.domain.service.SeedPlaceProvider
import io.soo.bueongi.core.place.api.controller.PlaceController

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(PlaceController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    PlaceSearchService::class,
    SeedPlaceProvider::class,
    ApiControllerAdvice::class,
    PlaceControllerTest.DisabledRemoteConfig::class,
)
class PlaceControllerTest(
    @param:Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `검색어가 일치하면 200 과 PlaceItem 배열을 반환`() {
        mockMvc.get("/api/places/search") { param("keyword", "강남역") }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$[0].name") { exists() }
                jsonPath("$[0].address") { exists() }
                jsonPath("$[0].lat") { isNumber() }
                jsonPath("$[0].lng") { isNumber() }
                jsonPath("$[?(@.name == '강남역 2번 출구')]") { exists() }
            }
    }

    @Test
    fun `keyword 파라미터가 없으면 200 과 빈 배열`() {
        mockMvc.get("/api/places/search")
            .andExpect {
                status { isOk() }
                jsonPath("$") { isArray() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `일치 항목이 없으면 200 과 빈 배열`() {
        mockMvc.get("/api/places/search") { param("keyword", "존재하지않는장소xyz") }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `검색어가 최대 길이를 초과하면 400 과 INVALID_KEYWORD 표준 스키마`() {
        val tooLong: String = "가".repeat(PlaceSearchService.MAX_KEYWORD_LENGTH + 1)
        mockMvc.get("/api/places/search") { param("keyword", tooLong) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("Invalid Keyword") }
                jsonPath("$.code") { value("INVALID_KEYWORD") }
                jsonPath("$.status") { value(400) }
                jsonPath("$.errors[0].field") { value("keyword") }
                jsonPath("$.errors[0].message") { exists() }
                jsonPath("$.errors[0].rejectedValue") { value(PlaceSearchService.MAX_KEYWORD_LENGTH + 1) }
            }
    }

    @TestConfiguration
    class DisabledRemoteConfig {
        @Bean
        fun remotePlaceProvider(): RemotePlaceProvider = object : RemotePlaceProvider {
            override val isEnabled: Boolean = false
            override fun search(keyword: String): List<PlaceItem> =
                throw AssertionError("disabled remote 는 호출되면 안 된다")
        }
    }
}
