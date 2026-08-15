package io.soo.bueongi.route

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.facility.FakeFacilitySource
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteFacilityServiceTest {

    private val origin: GeoPoint = GeoPoint(37.5, 127.000)
    private val destination: GeoPoint = GeoPoint(37.5, 127.020)

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
            FacilityType.LAMP to listOf(
                FacilityPoint(FacilityType.LAMP, 37.5, 127.014, name = "가로등-1"),
            ),
            FacilityType.PEDESTRIAN_SIGNAL to listOf(
                FacilityPoint(FacilityType.PEDESTRIAN_SIGNAL, 37.5, 127.016, name = "보행등"),
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
        assertThat(res.summary.lamp).isEqualTo(res.pois.count { it.type == FacilityPoiType.LAMP })
        assertThat(res.summary.pedestrianSignal)
            .isEqualTo(res.pois.count { it.type == FacilityPoiType.PEDESTRIAN_SIGNAL })
    }

    @Test
    fun `회랑 실측만 집계한다(추정 없음) - 실 데이터대로 개수가 정해진다`() {
        val res = request("safe", mixedSource()).summary
        assertThat(res.cctv).isEqualTo(2)
        assertThat(res.safehouse).isEqualTo(1)
        assertThat(res.bell).isEqualTo(1)
        assertThat(res.store).isEqualTo(0)
        assertThat(res.police).isEqualTo(0)
        assertThat(res.lamp).isEqualTo(1)
        assertThat(res.pedestrianSignal).isEqualTo(1)
    }

    @Test
    fun `편의점(store)은 지도에서 제외되어 어떤 경우에도 마커로 생성되지 않는다`() {
        val res = request("safe", mixedSource())
        assertThat(res.pois).noneMatch { it.type == FacilityPoiType.STORE }
        assertThat(res.summary.store).isEqualTo(0)
    }

    @Test
    fun `실 시설이 하나도 없으면 시설 요약은 모두 0이고 화면은 텅 비지 않는다(start-end 유지)`() {
        val res = request("safe", FakeFacilitySource())
        val s = res.summary
        assertThat(s.total).isEqualTo(0)
        assertThat(s.cctv + s.bell + s.store + s.police + s.safehouse + s.lamp + s.pedestrianSignal).isEqualTo(0)
        assertThat(res.pois.map { it.type }).containsExactlyInAnyOrder(FacilityPoiType.START, FacilityPoiType.END)
    }

    @Test
    fun `total 은 안심시설 합계이며 start end 를 제외한다`() {
        val res = request("safe", mixedSource())
        val s = res.summary
        assertThat(s.total)
            .isEqualTo(s.cctv + s.bell + s.store + s.police + s.safehouse + s.lamp + s.pedestrianSignal)
        assertThat(s.total).isEqualTo(res.pois.size - 2)
    }

    @Test
    fun `유형별 시설 개수는 마커 상한 12 를 넘지 않는다`() {
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
        val res = request("safe", mixedSource())
        val cctv = res.pois.filter { it.type == FacilityPoiType.CCTV }
        assertThat(cctv).hasSize(2)
        assertThat(cctv.map { it.lng }).containsExactlyInAnyOrder(127.005, 127.010)
        assertThat(cctv).allMatch { it.lat == 37.5 }
        val safehouse = res.pois.first { it.type == FacilityPoiType.SAFEHOUSE }
        assertThat(safehouse.name).isEqualTo("안심지킴이집-1")
    }
}
