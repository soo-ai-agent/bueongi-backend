package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.enums.RouteType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteTypeCatalogTest {
    private val catalog = RouteTypeCatalog()

    @Test
    fun `apiType 으로 매핑`() {
        assertThat(catalog.typeOf("safe")).isEqualTo(RouteType.SAFE)
        assertThat(catalog.typeOf("main")).isEqualTo(RouteType.MAIN)
        assertThat(catalog.typeOf("fast")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `id 문자열로 매핑`() {
        assertThat(catalog.typeOf("1")).isEqualTo(RouteType.SAFE)
        assertThat(catalog.typeOf("2")).isEqualTo(RouteType.MAIN)
        assertThat(catalog.typeOf("3")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `대소문자 무시 및 공백 트림`() {
        assertThat(catalog.typeOf("  SAFE ")).isEqualTo(RouteType.SAFE)
        assertThat(catalog.typeOf("Fast")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `null 또는 미상 값은 안전 우선 SAFE 기본`() {
        assertThat(catalog.typeOf(null)).isEqualTo(RouteType.SAFE)
        assertThat(catalog.typeOf("")).isEqualTo(RouteType.SAFE)
        assertThat(catalog.typeOf("unknown")).isEqualTo(RouteType.SAFE)
    }

    @Test
    fun `안전 경로일수록 우회 계수가 크고 최단일수록 작다`() {
        assertThat(catalog.displayOf(RouteType.SAFE).distanceFactor)
            .isGreaterThan(catalog.displayOf(RouteType.MAIN).distanceFactor)
        assertThat(catalog.displayOf(RouteType.MAIN).distanceFactor)
            .isGreaterThan(catalog.displayOf(RouteType.FAST).distanceFactor)
    }
}
