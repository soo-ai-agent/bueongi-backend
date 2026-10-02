package io.soo.bueongi.api

import io.soo.bueongi.client.tmap.TmapPlace
import io.soo.bueongi.client.tmap.TmapPlaceClient
import io.soo.bueongi.core.common.error.ApiControllerAdvice
import io.soo.bueongi.core.common.event.ApiEventPublisher
import io.soo.bueongi.core.place.api.controller.PlaceController
import io.soo.bueongi.core.place.api.request.PlaceSearchRequest
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@Tag("context")
@WebMvcTest(PlaceController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    PlaceSearchService::class,
    ApiControllerAdvice::class,
    PlaceControllerTest.StubRemoteConfig::class,
)
class PlaceControllerTest(@param:Autowired private val mockMvc: MockMvc) {
    @MockitoBean
    private lateinit var apiEvents: ApiEventPublisher

    @Test
    fun `검색어가 일치하면 200 과 장소 배열을 반환`() {
        mockMvc.get("/api/places/search") { param("keyword", "강남역") }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$[0].name") { value("강남역 2번 출구") }
                jsonPath("$[0].address") { exists() }
                jsonPath("$[0].lat") { isNumber() }
                jsonPath("$[0].lng") { isNumber() }
            }
    }

    @Test
    fun `keyword 파라미터가 없으면 400 과 VALIDATION_FAILED`() {
        mockMvc.get("/api/places/search")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
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
        val tooLong: String = "가".repeat(PlaceSearchRequest.MAX_KEYWORD_LENGTH + 1)
        mockMvc.get("/api/places/search") { param("keyword", tooLong) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("Invalid Keyword") }
                jsonPath("$.code") { value("INVALID_KEYWORD") }
                jsonPath("$.status") { value(400) }
                jsonPath("$.errors[0].field") { value("keyword") }
                jsonPath("$.errors[0].message") { exists() }
                jsonPath("$.errors[0].rejectedValue") { value(PlaceSearchRequest.MAX_KEYWORD_LENGTH + 1) }
            }
    }

    @Test
    fun `외부 검색이 실패해 503 이면 Tmap 장소검색 실패 알림을 보낸다`() {
        mockMvc.get("/api/places/search") { param("keyword", "장애") }
            .andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("PLACE_SEARCH_UNAVAILABLE") }
            }

        verify(apiEvents).placeSearchUnavailable(any())
    }

    @TestConfiguration
    class StubRemoteConfig {
        @Bean
        fun tmapPlaceClient(): TmapPlaceClient {
            val client = mock<TmapPlaceClient>()
            whenever(client.search("강남역"))
                .thenReturn(listOf(TmapPlace("강남역 2번 출구", "서울 강남구 강남대로 396", 37.4979, 127.0276)))
            whenever(client.search("존재하지않는장소xyz")).thenReturn(emptyList())
            whenever(client.search("장애")).thenThrow(IllegalStateException("tmap down"))
            return client
        }
    }
}
