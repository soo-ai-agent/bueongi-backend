package io.soo.bueongi.core.route

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.api.request.RouteRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteRequestMetaAbsorptionTest {
    private val mapper = jacksonObjectMapper()

    private val origin = LatLng(37.4979, 127.0276)
    private val destination = NamedLatLng(37.5572, 126.9245, name = "집")

    @Test
    fun `미상 문자열 routeType safetyPreference 는 안전 우선 기본으로 흡수된다`() {
        val request =
            RouteRequest(
                rawOrigin = origin,
                rawDestination = destination,
                rawRouteType = "unknown",
                rawSafetyPreference = "nope",
            )

        assertThat(request.routeType).isEqualTo(RouteType.SAFE)
        assertThat(request.safetyPreference).isEqualTo(SafetyPreference.SAFE)
    }

    @Test
    fun `routeType safetyPreference 키가 없으면 안전 우선 기본이다`() {
        val json = """{"origin":{"lat":37.4979,"lng":127.0276},"destination":{"lat":37.5572,"lng":126.9245}}"""

        val request = mapper.readValue<RouteRequest>(json)

        assertThat(request.routeType).isEqualTo(RouteType.SAFE)
        assertThat(request.safetyPreference).isEqualTo(SafetyPreference.SAFE)
    }

    @Test
    fun `유효 문자열은 그대로 통과한다 - 비회귀`() {
        val request =
            RouteRequest(
                rawOrigin = origin,
                rawDestination = destination,
                rawRouteType = "fast",
                rawSafetyPreference = "safest",
            )

        assertThat(request.routeType).isEqualTo(RouteType.FAST)
        assertThat(request.safetyPreference).isEqualTo(SafetyPreference.SAFEST)
    }

    @Test
    fun `비문자 숫자 입력은 400 이 아니라 기존처럼 기본값으로 흡수된다`() {
        val json: String =
            """
            {"origin":{"lat":37.4979,"lng":127.0276},
             "destination":{"lat":37.5572,"lng":126.9245,"name":"집"},
             "routeType":999,"safetyPreference":42}
            """.trimIndent()

        val request = mapper.readValue<RouteRequest>(json)

        assertThat(request.routeType).isEqualTo(RouteType.SAFE)
        assertThat(request.safetyPreference).isEqualTo(SafetyPreference.SAFE)
    }
}
