package io.soo.bueongi.places

import io.soo.bueongi.common.ApiExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * [PlaceController] 슬라이스 테스트(@WebMvcTest). 실 서비스([PlaceSearchService]) + 시드 소스를 import 해
 * GET /api/places/search 의 HTTP 계약(상태코드 + PlaceItem JSON shape)을 고정한다.
 * 외부 키 없는 환경(remote disabled)을 가정 — 슬라이스에 네트워크 autoconfig 를 끌어오지 않는다.
 */
@WebMvcTest(PlaceController::class)
@Import(
    PlaceSearchService::class,
    SeedPlaceProvider::class,
    ApiExceptionHandler::class,
    PlaceControllerTest.DisabledRemoteConfig::class,
)
class PlaceControllerTest(
    @Autowired private val mockMvc: MockMvc,
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
        val tooLong = "가".repeat(PlaceSearchService.MAX_KEYWORD_LENGTH + 1)
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

    /** 외부 키 미설정 상태(시드 폴백 경로)를 슬라이스에 제공. */
    @TestConfiguration
    class DisabledRemoteConfig {
        @Bean
        fun remotePlaceProvider(): RemotePlaceProvider = object : RemotePlaceProvider {
            override val isEnabled = false
            override fun search(keyword: String): List<PlaceItem> =
                throw AssertionError("disabled remote 는 호출되면 안 된다")
        }
    }
}
