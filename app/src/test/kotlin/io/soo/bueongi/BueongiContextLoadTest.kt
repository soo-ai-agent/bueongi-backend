package io.soo.bueongi

import io.soo.bueongi.core.place.domain.error.PlaceSearchUnavailableException
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Tag("context")
class BueongiContextLoadTest(@param:Autowired private val placeSearchService: PlaceSearchService) {
    @Test
    fun `테스트 환경은 외부 키 미설정 - 장소 검색은 사용 불가를 알린다`() {
        assertThatThrownBy { placeSearchService.search("강남") }
            .isInstanceOf(PlaceSearchUnavailableException::class.java)
    }
}
