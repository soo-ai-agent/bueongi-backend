package io.soo.bueongi.core.route

import io.soo.bueongi.core.route.api.request.toRouteType
import io.soo.bueongi.core.enums.RouteType

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteQueryParamsTest {

    @Test
    fun `apiType 으로 매핑`() {
        assertThat(toRouteType("safe")).isEqualTo(RouteType.SAFE)
        assertThat(toRouteType("main")).isEqualTo(RouteType.MAIN)
        assertThat(toRouteType("fast")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `id 문자열로 매핑`() {
        assertThat(toRouteType("1")).isEqualTo(RouteType.SAFE)
        assertThat(toRouteType("2")).isEqualTo(RouteType.MAIN)
        assertThat(toRouteType("3")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `대소문자 무시 및 공백 트림`() {
        assertThat(toRouteType("  SAFE ")).isEqualTo(RouteType.SAFE)
        assertThat(toRouteType("Fast")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `null 또는 미상 값은 안전 우선 SAFE 기본`() {
        assertThat(toRouteType(null)).isEqualTo(RouteType.SAFE)
        assertThat(toRouteType("")).isEqualTo(RouteType.SAFE)
        assertThat(toRouteType("unknown")).isEqualTo(RouteType.SAFE)
    }

    @Test
    fun `안전 경로일수록 우회 계수가 크고 최단일수록 작다`() {
        assertThat(RouteType.SAFE.distanceFactor)
            .isGreaterThan(RouteType.MAIN.distanceFactor)
        assertThat(RouteType.MAIN.distanceFactor)
            .isGreaterThan(RouteType.FAST.distanceFactor)
    }
}
