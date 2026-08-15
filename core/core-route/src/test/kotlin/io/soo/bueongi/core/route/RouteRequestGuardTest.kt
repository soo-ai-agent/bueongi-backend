package io.soo.bueongi.core.route

import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.api.request.toValidatedQuery

import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint

import io.soo.bueongi.core.common.error.OriginRequiredException
import io.soo.bueongi.core.common.error.SameOriginDestinationException
import io.soo.bueongi.core.common.error.TripTooFarException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RouteRequestGuardTest {

    @Test
    fun `정상 입력이면 비null 좌표의 도메인 질의를 돌려준다`() {
        val request: RouteRequest = RouteRequest(origin = LatLng(37.4979, 127.0276), destination = NamedLatLng(37.5572, 126.9245, name = "집"))
        val query = request.toValidatedQuery(RouteTypeCatalog())
        assertThat(query.origin).isEqualTo(GeoPoint(37.4979, 127.0276))
        assertThat(query.destination).isEqualTo(GeoPoint(37.5572, 126.9245))
        assertThat(query.destinationName).isEqualTo("집")
    }

    @Test
    fun `origin 누락이면 ORIGIN_REQUIRED`() {
        val request: RouteRequest = RouteRequest(origin = null, destination = NamedLatLng(37.5572, 126.9245))
        assertThatThrownBy { request.toValidatedQuery(RouteTypeCatalog()) }
            .isInstanceOf(OriginRequiredException::class.java)
    }

    @Test
    fun `출발=도착이면 SAME_ORIGIN_DESTINATION`() {
        val request: RouteRequest = RouteRequest(
            origin = LatLng(37.5, 127.0),
            destination = NamedLatLng(37.5, 127.0),
        )
        assertThatThrownBy { request.toValidatedQuery(RouteTypeCatalog()) }
            .isInstanceOf(SameOriginDestinationException::class.java)
    }

    @Test
    fun `원거리 상한 초과면 TRIP_TOO_FAR`() {
        val request: RouteRequest = RouteRequest(
            origin = LatLng(37.0, 127.0),
            destination = NamedLatLng(38.5, 127.0),
        )
        assertThatThrownBy { request.toValidatedQuery(RouteTypeCatalog()) }
            .isInstanceOf(TripTooFarException::class.java)
    }

    @Test
    fun `가드 순서 - origin 누락이 거리 가드보다 먼저다`() {
        val request: RouteRequest = RouteRequest(origin = null, destination = NamedLatLng(37.5, 127.0))
        assertThatThrownBy { request.toValidatedQuery(RouteTypeCatalog()) }
            .isInstanceOf(OriginRequiredException::class.java)
    }
}
