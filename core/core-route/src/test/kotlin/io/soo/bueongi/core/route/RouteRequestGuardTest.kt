package io.soo.bueongi.core.route

import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.error.CoordinateOutOfRangeException
import io.soo.bueongi.core.route.domain.error.SameOriginDestinationException
import io.soo.bueongi.core.route.domain.error.TripTooFarException
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RouteRequestGuardTest {
    @Test
    fun `정상 입력이면 비null 좌표와 기본 경로 유형을 갖는다`() {
        val request = RouteRequest(LatLng(37.4979, 127.0276), NamedLatLng(37.5572, 126.9245, name = "집"))

        assertThat(request.origin).isEqualTo(GeoPoint(37.4979, 127.0276))
        assertThat(request.destination).isEqualTo(GeoPoint(37.5572, 126.9245))
        assertThat(request.destinationName).isEqualTo("집")
        assertThat(request.routeType).isEqualTo(RouteType.SAFE)
    }

    @Test
    fun `좌표 범위를 벗어나면 CoordinateOutOfRangeException`() {
        assertThatThrownBy { RouteRequest(LatLng(100.0, 127.0), NamedLatLng(37.5, 126.9)) }
            .isInstanceOf(CoordinateOutOfRangeException::class.java)
    }

    @Test
    fun `출발=도착이면 SameOriginDestinationException`() {
        assertThatThrownBy { RouteRequest(LatLng(37.5, 127.0), NamedLatLng(37.5, 127.0)) }
            .isInstanceOf(SameOriginDestinationException::class.java)
    }

    @Test
    fun `원거리 상한 초과면 TripTooFarException`() {
        assertThatThrownBy { RouteRequest(LatLng(37.0, 127.0), NamedLatLng(38.5, 127.0)) }
            .isInstanceOf(TripTooFarException::class.java)
    }
}
