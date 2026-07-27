package io.soo.bueongi.domain.route

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.domain.facility.FakeFacilitySource
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [RouteFacilityService.alongRoute] 단위 테스트 — 정직화(밀도 추정 폐기) 후 계약:
 * 출발→도착 직선 회랑(30m)의 **실측 시설만** 집계한다. 요약(summary)은 생성된 POI 를 그대로 세므로
 * 항상 지도 표시(POI)와 일치하고, 실 데이터가 없는 유형(편의점 등)·회랑에 시설이 없는 유형은 0 이다.
 */
class RouteFacilityServiceTest {

    // 동서(위도 37.5 고정) 직선 경로. 회랑 안(위도 37.5 선상)에 시설을 놓아 실측이 잡히게 한다.
    private val origin = GeoPoint(37.5, 127.000)
    private val destination = GeoPoint(37.5, 127.020)

    private fun request(routeType: String?, source: FakeFacilitySource) =
        RouteFacilityService(RouteMarkerBuilder(source)).alongRoute(
            RouteQuery(
                origin = origin,
                destination = destination,
                destinationName = "목적지",
                routeType = RouteType.from(routeType),
                safetyPreference = SafetyPreference.SAFE,
            ),
        )

    /** 직선(위도 37.5) 회랑 안에 CCTV 2·안심집 1·비상벨 1 을 놓은 소스. store/police 는 없음. */
    private fun mixedSource() = FakeFacilitySource(
        mapOf(
            FacilityType.CCTV to listOf(
                FacilityPoint(FacilityType.CCTV, 37.5, 127.005, name = "CCTV-A"),
                FacilityPoint(FacilityType.CCTV, 37.5, 127.010, name = "CCTV-B"),
            ),
            FacilityType.SAFEHOUSE to listOf(
                FacilityPoint(FacilityType.SAFEHOUSE, 37.5, 127.008, name = "안심지킴이집-1"),
            ),
            FacilityType.BELL to listOf(
                FacilityPoint(FacilityType.BELL, 37.5, 127.012),
            ),
        ),
    )

    @Test
    fun `start 와 end POI 가 항상 포함된다`() {
        val res = request("safe", FakeFacilitySource())
        assertThat(res.pois.map { it.type }).contains(FacilityPoiType.START, FacilityPoiType.END)
        val start = res.pois.first { it.type == FacilityPoiType.START }
        val end = res.pois.first { it.type == FacilityPoiType.END }
        assertThat(start.lat).isEqualTo(origin.lat)
        assertThat(end.lng).isEqualTo(destination.lng)
    }

    @Test
    fun `요약은 생성된 POI 집계와 정확히 일치한다`() {
        val res = request("safe", mixedSource())
        assertThat(res.summary.cctv).isEqualTo(res.pois.count { it.type == FacilityPoiType.CCTV })
        assertThat(res.summary.bell).isEqualTo(res.pois.count { it.type == FacilityPoiType.BELL })
        assertThat(res.summary.store).isEqualTo(res.pois.count { it.type == FacilityPoiType.STORE })
        assertThat(res.summary.police).isEqualTo(res.pois.count { it.type == FacilityPoiType.POLICE })
        assertThat(res.summary.safehouse).isEqualTo(res.pois.count { it.type == FacilityPoiType.SAFEHOUSE })
    }

    @Test
    fun `회랑 실측만 집계한다(추정 없음) - 실 데이터대로 개수가 정해진다`() {
        val res = request("safe", mixedSource()).summary
        assertThat(res.cctv).isEqualTo(2)
        assertThat(res.safehouse).isEqualTo(1)
        assertThat(res.bell).isEqualTo(1)
        // store 는 지도에서 제외(마커 미생성)이라 항상 0. police 도 소스에 없어 0.
        assertThat(res.store).isEqualTo(0)
        assertThat(res.police).isEqualTo(0)
    }

    @Test
    fun `편의점(store)은 지도에서 제외되어 어떤 경우에도 마커로 생성되지 않는다`() {
        // 사용자 결정: 편의점 제외. FacilityType.STORE 자체가 없어 회랑 질의 대상이 아니므로
        // 실 시설이 풍부한 소스에서도 store POI 는 0 이고, 요약 store 필드(계약 유지)는 항상 0 이다.
        val res = request("safe", mixedSource())
        assertThat(res.pois).noneMatch { it.type == FacilityPoiType.STORE }
        assertThat(res.summary.store).isEqualTo(0)
    }

    @Test
    fun `실 시설이 하나도 없으면 시설 요약은 모두 0이고 화면은 텅 비지 않는다(start-end 유지)`() {
        // 정직화 회귀 방어: 밀도 추정 폴백이 사라져 실측 0 이면 시설 0 이 맞다. 단 크래시·빈배열 없이
        // 출발/도착 POI 는 유지돼 지도가 그려진다(카드는 0 유형 칩을 숨길 뿐 화면이 깨지지 않는다).
        val res = request("safe", FakeFacilitySource())
        val s = res.summary
        assertThat(s.total).isEqualTo(0)
        assertThat(s.cctv + s.bell + s.store + s.police + s.safehouse).isEqualTo(0)
        assertThat(res.pois.map { it.type }).containsExactlyInAnyOrder(FacilityPoiType.START, FacilityPoiType.END)
    }

    @Test
    fun `total 은 안심시설 합계이며 start end 를 제외한다`() {
        val res = request("safe", mixedSource())
        val s = res.summary
        assertThat(s.total).isEqualTo(s.cctv + s.bell + s.store + s.police + s.safehouse)
        // start/end 는 집계에서 제외 → total 은 POI 총개수보다 2 작다.
        assertThat(s.total).isEqualTo(res.pois.size - 2)
    }

    @Test
    fun `유형별 시설 개수는 마커 상한 12 를 넘지 않는다`() {
        // 회랑 안에 CCTV 20개를 촘촘히 놓아도 응답·지도 방어를 위해 유형당 상한(12)으로 클램프된다.
        val many = (1..20).map { FacilityPoint(FacilityType.CCTV, 37.5, 127.000 + it * 0.0005) }
        val res = request("safe", FakeFacilitySource(mapOf(FacilityType.CCTV to many)))
        assertThat(res.summary.cctv).isLessThanOrEqualTo(12)
    }

    @Test
    fun `정규화 좌표 x y 는 0부터 100 범위 안에 있다`() {
        val res = request("main", mixedSource())
        assertThat(res.pois).allMatch { it.x in 0..100 && it.y in 0..100 }
    }

    @Test
    fun `결정론적 - 같은 입력은 같은 POI 결과`() {
        val src = mixedSource()
        assertThat(request("safe", src)).isEqualTo(request("safe", src))
    }

    @Test
    fun `실측 시설 POI 는 실 좌표(회랑 안)로 표시된다`() {
        // 정직화 이후 핵심: 표시 좌표는 지어낸 직선 위 점이 아니라 실 시설의 실 좌표여야 한다.
        val res = request("safe", mixedSource())
        val cctv = res.pois.filter { it.type == FacilityPoiType.CCTV }
        assertThat(cctv).hasSize(2)
        // 소스에 넣은 실 좌표(위도 37.5, 경도 127.005/127.010)가 그대로 실린다.
        assertThat(cctv.map { it.lng }).containsExactlyInAnyOrder(127.005, 127.010)
        assertThat(cctv).allMatch { it.lat == 37.5 }
        // 안심집은 실 이름 메타가 보존된다.
        val safehouse = res.pois.first { it.type == FacilityPoiType.SAFEHOUSE }
        assertThat(safehouse.name).isEqualTo("안심지킴이집-1")
    }
}
