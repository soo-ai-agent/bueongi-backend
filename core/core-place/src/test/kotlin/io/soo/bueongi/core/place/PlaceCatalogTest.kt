package io.soo.bueongi.core.place

import io.soo.bueongi.core.place.domain.model.SEED_PLACE_CATALOG
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlaceCatalogTest {
    private val koreaLat = 33.0..39.0
    private val koreaLng = 124.0..132.0

    @Test
    fun `카탈로그는 비어있지 않다 - 폴백 검색 소스가 반환할 데이터 보장`() {
        assertThat(SEED_PLACE_CATALOG).isNotEmpty()
    }

    @Test
    fun `모든 시드의 이름과 주소는 공백이 아니다 - 검색이 이름·주소로 매칭`() {
        assertThat(SEED_PLACE_CATALOG).allSatisfy { place ->
            assertThat(place.name).isNotBlank()
            assertThat(place.address).isNotBlank()
        }
    }

    @Test
    fun `모든 시드 좌표는 대한민국 본토 안이다 - 위경도 swap·도메인 이탈 오타 차단`() {
        assertThat(SEED_PLACE_CATALOG).allSatisfy { place ->
            assertThat(place.lat).`as`("lat of ${place.name}").isBetween(koreaLat.start, koreaLat.endInclusive)
            assertThat(place.lng).`as`("lng of ${place.name}").isBetween(koreaLng.start, koreaLng.endInclusive)
        }
    }

    @Test
    fun `시드 항목은 이름+주소 기준 중복이 없다`() {
        val keys = SEED_PLACE_CATALOG.map { it.name to it.address }
        assertThat(keys).doesNotHaveDuplicates()
    }
}
