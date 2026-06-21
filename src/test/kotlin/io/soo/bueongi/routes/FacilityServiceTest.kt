package io.soo.bueongi.routes

import io.soo.bueongi.common.SameOriginDestinationException
import io.soo.bueongi.common.TripTooFarException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * [FacilityService.alongRoute] 단위 테스트. 핵심 계약: 요약(summary)은 생성된 POI 를 실제로 집계하므로
 * 항상 지도 표시(POI)와 일치한다. 좌표/개수는 결정론적이며 유형별 상한(MAX_PER_TYPE=8)을 넘지 않는다.
 */
class FacilityServiceTest {

    private val service = FacilityService()

    private fun request(routeType: String?) = RouteRequest(
        origin = LatLng(37.4979, 127.0276),       // 강남역
        destination = NamedLatLng(37.5572, 126.9245, "홍대입구역"),
        routeType = routeType,
    )

    @Test
    fun `start 와 end POI 가 항상 포함된다`() {
        val res = service.alongRoute(request("safe"))
        assertThat(res.pois.map { it.type }).contains("start", "end")
        val start = res.pois.first { it.type == "start" }
        val end = res.pois.first { it.type == "end" }
        assertThat(start.lat).isEqualTo(37.4979)
        assertThat(end.lat).isEqualTo(37.5572)
    }

    @Test
    fun `요약은 생성된 POI 집계와 정확히 일치한다`() {
        val res = service.alongRoute(request("safe"))
        assertThat(res.summary.cctv).isEqualTo(res.pois.count { it.type == "cctv" })
        assertThat(res.summary.bell).isEqualTo(res.pois.count { it.type == "bell" })
        assertThat(res.summary.store).isEqualTo(res.pois.count { it.type == "store" })
        assertThat(res.summary.police).isEqualTo(res.pois.count { it.type == "police" })
    }

    @Test
    fun `total 은 안심시설 합계이며 start end 를 제외한다`() {
        val res = service.alongRoute(request("safe"))
        val s = res.summary
        assertThat(s.total).isEqualTo(s.cctv + s.bell + s.store + s.police)
        // start/end 는 집계에서 제외 → total 은 POI 총개수보다 2 작다.
        assertThat(s.total).isEqualTo(res.pois.size - 2)
    }

    @Test
    fun `안전 경로가 최단 경로보다 안심시설이 많다(밀도 차이)`() {
        val safe = service.alongRoute(request("safe")).summary.total
        val fast = service.alongRoute(request("fast")).summary.total
        assertThat(safe).isGreaterThan(fast)
    }

    @Test
    fun `유형별 시설 개수는 상한 8 을 넘지 않는다`() {
        // 매우 긴 경로(상한 100km 바로 아래 ~89km)로도 상한이 지켜지는지 확인.
        val long = RouteRequest(
            origin = LatLng(37.0, 127.0),
            destination = NamedLatLng(37.8, 127.0),   // 위도 0.8도 ≈ 89km (< MAX_TRIP_DISTANCE_METERS)
            routeType = "safe",
        )
        val res = service.alongRoute(long)
        for (type in listOf("cctv", "bell", "store", "police")) {
            assertThat(res.pois.count { it.type == type })
                .describedAs("type=%s", type)
                .isLessThanOrEqualTo(8)
        }
    }

    @Test
    fun `정규화 좌표 x y 는 0부터 100 범위 안에 있다`() {
        val res = service.alongRoute(request("main"))
        assertThat(res.pois).allMatch { it.x in 0..100 && it.y in 0..100 }
    }

    @Test
    fun `결정론적 - 같은 입력은 같은 POI 결과`() {
        assertThat(service.alongRoute(request("safe")))
            .isEqualTo(service.alongRoute(request("safe")))
    }

    @Test
    fun `routeType 누락시 안전(safe) 기준으로 집계`() {
        assertThat(service.alongRoute(request(null)))
            .isEqualTo(service.alongRoute(request("safe")))
    }

    @Test
    fun `짧은 경로라도 추천(safe) 경로는 최소 안심시설을 보장한다(floor 보정)`() {
        // 안심 귀가 앱의 핵심 약속: 거리가 매우 짧아 밀도×거리 계산이 0 에 수렴해도
        // '추천 경로'는 최소한의 안심시설(cctv≥2, bell≥1, store≥1)을 보여줘야 한다.
        // (floorWhenSafe 보정이 사라지면 짧은 추천 경로가 '시설 0' 으로 보여 안전감을 해치는데,
        //  기존 테스트는 모두 11km 장거리 경로라 floor 가 발동하지 않아 이 불변식이 미고정이었다.)
        val shortSafe = RouteRequest(
            origin = LatLng(37.5, 127.0),
            destination = NamedLatLng(37.501, 127.0, "근처"),   // 위도 0.001도 ≈ 111m (밀도×거리만으론 bell/store=0)
            routeType = "safe",
        )
        val s = service.alongRoute(shortSafe).summary
        assertThat(s.cctv).describedAs("짧은 추천 경로 cctv floor").isGreaterThanOrEqualTo(2)
        assertThat(s.bell).describedAs("짧은 추천 경로 bell floor").isGreaterThanOrEqualTo(1)
        assertThat(s.store).describedAs("짧은 추천 경로 store floor").isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `floor 는 추천 경로 전용 - 같은 짧은 경로의 최단(fast)은 추천보다 시설이 적다`() {
        // 대조군: floorWhenSafe 보정이 SAFE 에만 적용됨을 고정. 같은 짧은 거리에서 fast 는 보정이 없어
        // 밀도×짧은거리 → bell/store 가 0 으로 떨어진다(추천 경로의 최소 보장과 대비).
        fun shortRoute(type: String) = RouteRequest(
            origin = LatLng(37.5, 127.0),
            destination = NamedLatLng(37.501, 127.0, "근처"),
            routeType = type,
        )
        val safe = service.alongRoute(shortRoute("safe")).summary
        val fast = service.alongRoute(shortRoute("fast")).summary
        // 짧은 경로에서 추천 경로는 최단 경로보다 안심시설이 엄격히 많다(floor 효과).
        assertThat(safe.total).isGreaterThan(fast.total)
        // floor 가 없는 fast 는 bell 이 0(밀도×짧은거리 → 0) — 추천 경로의 bell≥1 보장과 대비.
        assertThat(fast.bell).isEqualTo(0)
    }

    @Test
    fun `안심시설 POI 는 출발-도착 직선 경로 위에 놓인다(공선성 + 구간 내)`() {
        // 이 엔드포인트의 핵심 약속(alongRoute, '경로상' 안심시설): 표시되는 시설 좌표(lat/lng)는
        // 실제 귀가 경로(출발→도착 선분) 위에 있어야 한다. 사용자가 그 경로를 따라 걸으며 만나는 시설이기 때문.
        // 기존 테스트는 개수·x/y 범위·summary 일치만 고정할 뿐, 시설의 지리 좌표가 경로를 벗어나는지는 미고정이었다.
        // 배치 로직 리팩터(좌표 jitter 추가·lat/lng 스왑·오프셋 오류)로 시설이 경로 밖에 찍혀도 기존 테스트는 통과한다.
        val res = service.alongRoute(request("safe"))
        // request("safe") 와 동일한 출발/도착 좌표(non-null 로컬 고정).
        val oLat = 37.4979; val oLng = 127.0276
        val dLat = 37.5572; val dLng = 126.9245
        val facilities = res.pois.filter { it.type in listOf("cctv", "bell", "store", "police") }
        assertThat(facilities).describedAs("시설 POI 가 하나 이상 존재").isNotEmpty()

        val vLat = dLat - oLat
        val vLng = dLng - oLng
        for (poi in facilities) {
            // 공선성: (P-O) 와 (D-O) 의 외적(2D cross)이 0 이면 P 가 직선 OD 위에 있다.
            val cross = (poi.lat - oLat) * vLng - (poi.lng - oLng) * vLat
            assertThat(cross)
                .describedAs("시설 %s 가 출발-도착 직선 위(공선성)", poi.type)
                .isCloseTo(0.0, within(1e-9))
            // 구간 내: 연장선이 아니라 출발-도착 '사이'에 있어야 한다(바운딩 박스).
            assertThat(poi.lat).isBetween(minOf(oLat, dLat), maxOf(oLat, dLat))
            assertThat(poi.lng).isBetween(minOf(oLng, dLng), maxOf(oLng, dLng))
        }
    }

    @Test
    fun `출발지와 도착지가 같은 좌표면 SAME_ORIGIN_DESTINATION 예외`() {
        // compare 와 동일 가드: 같은 지점 경로엔 안심시설 집계가 무의미 → 422 로 거부.
        val same = RouteRequest(
            origin = LatLng(37.5, 127.0),
            destination = NamedLatLng(37.5, 127.0),
            routeType = "safe",
        )
        assertThatThrownBy { service.alongRoute(same) }
            .isInstanceOf(SameOriginDestinationException::class.java)
    }

    @Test
    fun `출발지-도착지가 최대 거리를 넘으면 TRIP_TOO_FAR 예외`() {
        // compare 와 동일 가드: 도보 귀가로 보기엔 비현실적 원거리(여기선 위도 1.5도 ≈ 167km > 100km) → 422 로 거부.
        val tooFar = RouteRequest(
            origin = LatLng(37.0, 127.0),
            destination = NamedLatLng(38.5, 127.0),
            routeType = "safe",
        )
        assertThatThrownBy { service.alongRoute(tooFar) }
            .isInstanceOf(TripTooFarException::class.java)
    }
}
