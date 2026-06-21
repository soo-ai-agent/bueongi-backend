package io.soo.bueongi.routes

import io.soo.bueongi.common.SameOriginDestinationException
import io.soo.bueongi.common.TripTooFarException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * [RouteService.compare] 단위 테스트. 항상 safe/main/fast 3개를 결정론적으로 만들고,
 * 응답 필드(프론트 MockRoute 계약)와 거리 정렬(안전>주요>최단)을 고정한다.
 */
class RouteServiceTest {

    private val service = RouteService()

    private fun request() = RouteRequest(
        origin = LatLng(37.4979, 127.0276),       // 강남역
        destination = NamedLatLng(37.5572, 126.9245, "홍대입구역"),
    )

    @Test
    fun `항상 safe main fast 3개 경로 반환`() {
        val routes = service.compare(request())
        assertThat(routes).hasSize(3)
        assertThat(routes.map { it.type }).containsExactly("safe", "main", "fast")
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
            if (dist.endsWith("km")) dist.removeSuffix("km").toDouble() * 1000
            else dist.removeSuffix("m").toDouble()
        assertThat(meters(routes.getValue("safe").dist))
            .isGreaterThan(meters(routes.getValue("fast").dist))
    }

    @Test
    fun `결정론적 - 같은 입력은 같은 결과`() {
        assertThat(service.compare(request())).isEqualTo(service.compare(request()))
    }

    @Test
    fun `출발지와 도착지가 같은 좌표면 SAME_ORIGIN_DESTINATION 예외`() {
        // 같은 지점으로의 "귀가 경로"는 의미가 없다 → 경로 생성 대신 422 로 거부(프론트 도착지 재선택 유도).
        val same = RouteRequest(
            origin = LatLng(37.5, 127.0),
            destination = NamedLatLng(37.5, 127.0),
        )
        assertThatThrownBy { service.compare(same) }
            .isInstanceOf(SameOriginDestinationException::class.java)
    }

    @Test
    fun `출발지와 도착지가 1m 이상 떨어지면 정상적으로 경로를 만든다`() {
        // 경계 방어: 임계값(1m) 이상이면 근거리여도 막지 않는다. 위도 0.001도 ≈ 111m.
        val near = RouteRequest(
            origin = LatLng(37.5, 127.0),
            destination = NamedLatLng(37.501, 127.0),
        )
        assertThat(service.compare(near)).hasSize(3)
    }

    @Test
    fun `출발지-도착지가 최대 거리를 넘으면 TRIP_TOO_FAR 예외`() {
        // 도보 귀가로 비현실적 원거리(위도 1.5도 ≈ 167km > 100km)는 도착지 오선택일 가능성이 커 422 로 거부.
        val tooFar = RouteRequest(
            origin = LatLng(37.0, 127.0),
            destination = NamedLatLng(38.5, 127.0),
        )
        assertThatThrownBy { service.compare(tooFar) }
            .isInstanceOf(TripTooFarException::class.java)
    }

    @Test
    fun `최대 거리 이내의 장거리 경로는 정상적으로 만든다`() {
        // 경계 방어: 상한(100km) 바로 아래(위도 0.8도 ≈ 89km)면 막지 않고 3개 경로를 만든다.
        val long = RouteRequest(
            origin = LatLng(37.0, 127.0),
            destination = NamedLatLng(37.8, 127.0),
        )
        assertThat(service.compare(long)).hasSize(3)
    }
}
