package io.soo.bueongi.api.v1.request

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.domain.route.RouteType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 요청 DTO 메타(routeType/safetyPreference)의 **관용 흡수 동작 보존** 회귀 가드.
 *
 * 이 두 필드는 와이어 경계에서 String 으로 유지된다(enum 역직렬화로 바꾸지 않는다) — 도메인은 이미
 * [RouteQuery] 가 enum([RouteType]/[SafetyPreference])으로 정규화하므로 타입 정합성은 도메인에서 달성돼 있고,
 * 경계를 enum 으로 바꾸면 미상/누락/비문자(숫자) 입력의 흡수(→기본값)가 역직렬화 에러(400)로 바뀔 위험이 있다.
 * 그 동작이 바뀌지 않았음을 이 테스트가 못 박는다.
 */
class RouteRequestMetaAbsorptionTest {

    private val mapper = jacksonObjectMapper()

    // 가드(출발지·동일좌표·원거리)를 모두 통과하는 유효 좌표(서초→마포).
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
        // enum 역직렬화였다면 숫자는 타입 불일치로 400 이 될 수 있다. String 유지라 Jackson 이 "999"로 흡수하고
        // from() 이 미상→SAFE 로 정규화한다(어떤 id 와도 겹치지 않는 999 를 골라 관용 흡수만 검증).
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
