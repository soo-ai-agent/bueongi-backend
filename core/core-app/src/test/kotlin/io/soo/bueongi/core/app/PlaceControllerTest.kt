package io.soo.bueongi.core.app

import io.soo.bueongi.core.common.error.ApiControllerAdvice
import io.soo.bueongi.core.place.api.controller.PlaceController
import io.soo.bueongi.core.place.api.request.PlaceSearchRequest
import io.soo.bueongi.core.place.api.response.PlaceResponse
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@Tag("context")
@WebMvcTest(PlaceController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiControllerAdvice::class)
class PlaceControllerTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var placeSearchService: PlaceSearchService

    @Test
    fun `검색 결과를 장소 배열로 응답한다`() {
        whenever(placeSearchService.search("강남역"))
            .thenReturn(listOf(PlaceResponse("강남역 2번 출구", "서울 강남구 강남대로 396", 37.4979, 127.0276)))

        mockMvc.get("/api/places/search") { param("keyword", "강남역") }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].name") { value("강남역 2번 출구") }
                jsonPath("$[0].address") { value("서울 강남구 강남대로 396") }
                jsonPath("$[0].lat") { value(37.4979) }
                jsonPath("$[0].lng") { value(127.0276) }
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
}
