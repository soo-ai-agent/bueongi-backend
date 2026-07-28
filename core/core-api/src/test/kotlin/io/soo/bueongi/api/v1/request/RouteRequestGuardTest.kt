package io.soo.bueongi.api.v1.request
import io.soo.bueongi.storage.db.core.facility.GeoPoint

import io.soo.bueongi.support.error.OriginRequiredException
import io.soo.bueongi.support.error.SameOriginDestinationException
import io.soo.bueongi.support.error.TripTooFarException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * [RouteRequest.toValidatedQuery] 단위 테스트 — compare/facilities 공용 입력 가드의 **단일 진실 원천**을 직접 고정한다.
 *
 * 세 엔드포인트(compare/safe-compare/facilities)가 이 변환만 거치므로, 가드의 종류·**순서**가
 * 여기서 고정되면 두 엔드포인트는 항상 동일한 검증을 거친다(한쪽에만 가드가 빠지는 드리프트 방지 회귀 가드).
 */
class RouteRequestGuardTest {

    @Test
    fun `정상 입력이면 비null 좌표의 도메인 질의를 돌려준다`() {
        val request: RouteRequest = RouteRequest(origin = LatLng(37.4979, 127.0276), destination = NamedLatLng(37.5572, 126.9245, name = "집"))
        val query = request.toValidatedQuery()
        assertThat(query.origin).isEqualTo(GeoPoint(37.4979, 127.0276))
        assertThat(query.destination).isEqualTo(GeoPoint(37.5572, 126.9245))
        assertThat(query.destinationName).isEqualTo("집")
    }

    @Test
    fun `origin 누락이면 ORIGIN_REQUIRED`() {
        val request: RouteRequest = RouteRequest(origin = null, destination = NamedLatLng(37.5572, 126.9245))
        assertThatThrownBy { request.toValidatedQuery() }
            .isInstanceOf(OriginRequiredException::class.java)
    }

    @Test
    fun `출발=도착이면 SAME_ORIGIN_DESTINATION`() {
        val request: RouteRequest = RouteRequest(
            origin = LatLng(37.5, 127.0),
            destination = NamedLatLng(37.5, 127.0),
        )
        assertThatThrownBy { request.toValidatedQuery() }
            .isInstanceOf(SameOriginDestinationException::class.java)
    }

    @Test
    fun `원거리 상한 초과면 TRIP_TOO_FAR`() {
        val request: RouteRequest = RouteRequest(
            origin = LatLng(37.0, 127.0),
            destination = NamedLatLng(38.5, 127.0),   // 위도 1.5도 ≈ 167km > 100km
        )
        assertThatThrownBy { request.toValidatedQuery() }
            .isInstanceOf(TripTooFarException::class.java)
    }

    @Test
    fun `가드 순서 - origin 누락이 거리 가드보다 먼저다`() {
        // origin 이 없으면 거리(동일좌표/원거리)를 따질 출발지조차 없다 → ORIGIN_REQUIRED 가 우선.
        // (destination 만으로는 SAME/TRIP 판정 불가하므로 순서가 뒤바뀌면 NPE 가 날 위험 → 순서를 못 박는다.)
        val request: RouteRequest = RouteRequest(origin = null, destination = NamedLatLng(37.5, 127.0))
        assertThatThrownBy { request.toValidatedQuery() }
            .isInstanceOf(OriginRequiredException::class.java)
    }
}
