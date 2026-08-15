package io.soo.bueongi.core.domain.route

import io.soo.bueongi.core.api.controller.v1.request.toValidatedQuery

import io.soo.bueongi.core.api.controller.v1.request.RouteRequest

import io.soo.bueongi.core.api.controller.v1.request.LatLng
import io.soo.bueongi.core.api.controller.v1.request.NamedLatLng
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.core.enums.SafetyPreference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteRequestMetaAbsorptionTest {

    private val mapper = jacksonObjectMapper()

    private val origin: LatLng = LatLng(37.4979, 127.0276)
    private val destination: NamedLatLng = NamedLatLng(37.5572, 126.9245, name = "집")

    @Test
    fun `미상 문자열 routeType safetyPreference 는 안전 우선 기본으로 흡수된다`() {
        val query = RouteRequest(
            origin = origin,
            destination = destination,
            routeType = "unknown",
            safetyPreference = "nope",
        ).toValidatedQuery()

        assertThat(query.routeType).isEqualTo(RouteType.SAFE)
        assertThat(query.safetyPreference).isEqualTo(SafetyPreference.SAFE)
    }

    @Test
    fun `누락 null routeType safetyPreference 는 안전 우선 기본으로 흡수된다`() {
        val query = RouteRequest(
            origin = origin,
            destination = destination,
            routeType = null,
            safetyPreference = null,
        ).toValidatedQuery()

        assertThat(query.routeType).isEqualTo(RouteType.SAFE)
        assertThat(query.safetyPreference).isEqualTo(SafetyPreference.SAFE)
    }

    @Test
    fun `유효 문자열은 그대로 통과한다 - 비회귀`() {
        val query = RouteRequest(
            origin = origin,
            destination = destination,
            routeType = "fast",
            safetyPreference = "safest",
        ).toValidatedQuery()

        assertThat(query.routeType).isEqualTo(RouteType.FAST)
        assertThat(query.safetyPreference).isEqualTo(SafetyPreference.SAFEST)
    }

    @Test
    fun `비문자 숫자 입력은 400 이 아니라 기존처럼 기본값으로 흡수된다`() {
        val json: String = """
            {"origin":{"lat":37.4979,"lng":127.0276},
             "destination":{"lat":37.5572,"lng":126.9245,"name":"집"},
             "routeType":999,"safetyPreference":42}
        """.trimIndent()

        val query = mapper.readValue<RouteRequest>(json).toValidatedQuery()

        assertThat(query.routeType).isEqualTo(RouteType.SAFE)
        assertThat(query.safetyPreference).isEqualTo(SafetyPreference.SAFE)
    }
}
