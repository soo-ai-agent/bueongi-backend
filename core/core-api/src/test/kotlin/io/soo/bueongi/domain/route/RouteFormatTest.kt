package io.soo.bueongi.domain.route

import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * [RouteFormat]·거리 계산 단위 테스트(하버사인 단일 출처 = SpatialMath). 거리·표기·소요시간은 프론트 표시(dist/time)와 직접 매핑되므로
 * 표기 형식과 경계(1km, 0거리)를 고정한다.
 */
class RouteFormatTest {

    @Test
    fun `haversine - 같은 좌표는 0m`() {
        assertThat(SpatialMath.haversineMeters(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))).isEqualTo(0.0)
    }

    @Test
    fun `haversine - 강남역에서 홍대입구역 직선거리는 약 11 2km`() {
        // 시드 카탈로그 좌표 기준 알려진 근사값(허용 오차 ±500m).
        val meters = SpatialMath.haversineMeters(GeoPoint(37.4979, 127.0276), GeoPoint(37.5572, 126.9245))
        assertThat(meters).isCloseTo(11_230.0, within(500.0))
    }

    @Test
    fun `haversine - 대칭성(방향 무관 동일 거리)`() {
        val ab = SpatialMath.haversineMeters(GeoPoint(37.4979, 127.0276), GeoPoint(37.5133, 127.1001))
        val ba = SpatialMath.haversineMeters(GeoPoint(37.5133, 127.1001), GeoPoint(37.4979, 127.0276))
        assertThat(ab).isCloseTo(ba, within(1e-6))
    }

    @Test
    fun `formatDistance - 1km 미만은 10m 단위 m 표기`() {
        assertThat(RouteFormat.formatDistance(0.0)).isEqualTo("0m")
        assertThat(RouteFormat.formatDistance(854.0)).isEqualTo("850m")
        assertThat(RouteFormat.formatDistance(857.0)).isEqualTo("860m")
        assertThat(RouteFormat.formatDistance(999.0)).isEqualTo("1000m")
    }

    @Test
    fun `formatDistance - 1km 이상은 소수1자리 km 표기`() {
        assertThat(RouteFormat.formatDistance(1000.0)).isEqualTo("1.0km")
        assertThat(RouteFormat.formatDistance(1234.0)).isEqualTo("1.2km")
    }

    @Test
    fun `formatDistance - 음수는 0m 로 방어`() {
        assertThat(RouteFormat.formatDistance(-100.0)).isEqualTo("0m")
    }

    @Test
    fun `walkMinutes - 67m per minute 환산, 최소 1분`() {
        assertThat(RouteFormat.walkMinutes(0.0)).isEqualTo(1)
        assertThat(RouteFormat.walkMinutes(67.0)).isEqualTo(1)
        assertThat(RouteFormat.walkMinutes(670.0)).isEqualTo(10)
        assertThat(RouteFormat.walkMinutes(-50.0)).isEqualTo(1)
    }
}
