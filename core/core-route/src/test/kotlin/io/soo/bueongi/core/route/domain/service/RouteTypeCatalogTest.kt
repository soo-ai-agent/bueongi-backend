package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.enums.RouteType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteTypeCatalogTest {
    private val catalog = RouteTypeCatalog()

    @Test
    fun `안전 경로일수록 우회 계수가 크고 최단일수록 작다`() {
        assertThat(catalog.displayOf(RouteType.SAFE).distanceFactor)
            .isGreaterThan(catalog.displayOf(RouteType.MAIN).distanceFactor)
        assertThat(catalog.displayOf(RouteType.MAIN).distanceFactor)
            .isGreaterThan(catalog.displayOf(RouteType.FAST).distanceFactor)
    }
}
