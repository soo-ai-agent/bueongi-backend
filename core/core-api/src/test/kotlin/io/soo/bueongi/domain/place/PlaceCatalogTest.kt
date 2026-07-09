package io.soo.bueongi.domain.place
import io.soo.bueongi.api.v1.request.RouteRequest
import io.soo.bueongi.client.kakao.KakaoPlaceClient

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 내장 시드 카탈로그([SEED_PLACE_CATALOG]) 의 도메인 불변식 회귀 가드.
 *
 * 왜 필요한가: 시드 카탈로그는 외부 키 미주입(MVP 기본) 시 **런타임에서 실제로 활성화되는 검색 소스**다.
 * 그런데 [SeedPlaceProvider] 는 [KakaoPlaceClient.toPlaceItem] 과 달리 좌표 유효성 필터링 없이
 * 카탈로그 좌표를 그대로 신뢰한다. 즉 시드 좌표는 검색결과 → [PlaceItem] → 프론트 →
 * `RouteRequest.destination` → 경로/안심시설 계산으로 **무검증 직행**한다.
 *
 * 외부 Kakao 응답 경계([KakaoPlaceProviderTest])와 클라이언트 JSON 경계([RouteControllerTest])는
 * 비유한/범위 밖 좌표를 막도록 이미 봉인됐지만, 정작 가장 자주 쓰이는 시드 경로는 가드가 없었다.
 * 누군가 시드 추가/수정 시 위경도를 뒤바꾸거나(예: lat=127, lng=37) 좌표 오타를 내면 검색은 "정상"
 * 장소를 반환하지만 지리는 garbage → haversine 거리 가드를 우회해 잘못된 안전경로를 만든다(안심앱 위험).
 * 이 갭을 시드 데이터 원천에서 직접 봉인한다(소스 무변경, 순수 가산적 가드).
 */
class PlaceCatalogTest {

    // 안심앱은 국내(수도권 중심) 귀가 서비스 — 모든 시드는 대한민국 본토 bbox 안이어야 한다.
    // 이 bbox 는 위경도 swap(swap 시 lat≈127 로 범위 초과)·도메인 이탈 오타를 함께 잡는다.
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
    fun `모든 시드 좌표는 유한하다 - NaN·Infinity 없음`() {
        assertThat(SEED_PLACE_CATALOG).allSatisfy { place ->
            assertThat(place.lat).isFinite()
            assertThat(place.lng).isFinite()
        }
    }

    @Test
    fun `모든 시드 좌표는 지리 범위 안이다 - 위도 -90~90 경도 -180~180`() {
        assertThat(SEED_PLACE_CATALOG).allSatisfy { place ->
            assertThat(place.lat).isBetween(-90.0, 90.0)
            assertThat(place.lng).isBetween(-180.0, 180.0)
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
