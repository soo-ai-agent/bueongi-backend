package io.soo.bueongi

import io.soo.bueongi.domain.place.KakaoRemotePlaceProvider
import io.soo.bueongi.domain.place.PlaceSearchService
import io.soo.bueongi.domain.place.RemotePlaceProvider
import io.soo.bueongi.domain.place.SeedPlaceProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class BueongiContextLoadTest(
    @Autowired private val placeSearchService: PlaceSearchService,
    @Autowired private val seedProvider: SeedPlaceProvider,
    @Autowired private val remoteProvider: RemotePlaceProvider,
) {

    @Test
    fun `핵심 빈이 모두 와이어된다`() {
        assertThat(placeSearchService).isNotNull
        assertThat(seedProvider).isNotNull
        assertThat(remoteProvider).isInstanceOf(KakaoRemotePlaceProvider::class.java)
    }

    @Test
    fun `테스트 환경은 외부 키 미설정 - 시드 폴백 경로 활성`() {
        assertThat(remoteProvider.isEnabled).isFalse()
        assertThat(placeSearchService.search("강남").map { it.name }).contains("강남역 2번 출구")
    }
}
