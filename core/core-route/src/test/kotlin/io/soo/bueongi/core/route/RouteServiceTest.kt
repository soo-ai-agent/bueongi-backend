package io.soo.bueongi.core.route

import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.enums.RouteType

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteServiceTest {

    private val service: RouteService = RouteService(RouteTypeCatalog())

    private fun request() = RouteQuery(
        origin = GeoPoint(37.4979, 127.0276),
        destination = GeoPoint(37.5572, 126.9245),
        destinationName = "홍대입구역",
        routeType = RouteType.SAFE,
        safetyPreference = SafetyPreference.SAFE,
    )

    @Test
    fun `항상 safe main fast 3개 경로 반환`() {
        val routes = service.compare(request())
        assertThat(routes).hasSize(3)
        assertThat(routes.map { it.type }).containsExactly(RouteType.SAFE, RouteType.MAIN, RouteType.FAST)
    }

    @Test
    fun `각 경로는 프론트 계약 필드를 채운다`() {
        val safe = service.compare(request()).first()
        assertThat(safe.id).isEqualTo("1")
        assertThat(safe.name).isEqualTo("추천 경로")
        assertThat(safe.time).matches("\\d+분")
        assertThat(safe.dist).matches("\\d+m|\\d+\\.\\d+km")
        assertThat(safe.desc).isNotBlank
        assertThat(safe.tags).isNotEmpty
    }

    @Test
    fun `안전 경로가 최단 경로보다 거리가 길다(우회 계수)`() {
        val routes = service.compare(request()).associateBy { it.type }
        fun meters(dist: String): Double =
            if (dist.endsWith("km")) {
                dist.removeSuffix("km").toDouble() * 1000
            } else {
                dist.removeSuffix("m").toDouble()
            }
        assertThat(meters(routes.getValue(RouteType.SAFE).dist))
            .isGreaterThan(meters(routes.getValue(RouteType.FAST).dist))
    }

    @Test
    fun `결정론적 - 같은 입력은 같은 결과`() {
        assertThat(service.compare(request())).isEqualTo(service.compare(request()))
    }

    @Test
    fun `출발지와 도착지가 1m 이상 떨어지면 정상적으로 경로를 만든다`() {
        val near = request().copy(origin = GeoPoint(37.5, 127.0), destination = GeoPoint(37.501, 127.0))
        assertThat(service.compare(near)).hasSize(3)
    }

    @Test
    fun `최대 거리 이내의 장거리 경로는 정상적으로 만든다`() {
        val long = request().copy(origin = GeoPoint(37.0, 127.0), destination = GeoPoint(37.8, 127.0))
        assertThat(service.compare(long)).hasSize(3)
    }

    @Test
    fun `compare 는 추천 요청을 compare 로 1회 집계하고 buildRoutes 는 집계하지 않는다`() {
        val recording: RecordingRouteStatsStore = RecordingRouteStatsStore()
        val recordingService: RouteService = RouteService(RouteTypeCatalog(), recording)

        recordingService.compare(request())
        assertThat(recording.records.map { it.endpoint }).containsExactly("compare")
        assertThat(recording.records.first().preference).isEqualTo(SafetyPreference.SAFE)
        assertThat(recording.records.first().destinationName).isEqualTo("홍대입구역")

        recording.records.clear()
        recordingService.buildRoutes(request())
        assertThat(recording.records).isEmpty()
    }
}
