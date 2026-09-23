package io.soo.bueongi.core.enums

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteTypeTest {
    @Test
    fun `apiType 으로 매핑한다`() {
        assertThat(RouteType.of("safe")).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.of("main")).isEqualTo(RouteType.MAIN)
        assertThat(RouteType.of("fast")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `프론트가 보내는 번호로도 매핑한다`() {
        assertThat(RouteType.of("1")).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.of("2")).isEqualTo(RouteType.MAIN)
        assertThat(RouteType.of("3")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `대소문자와 앞뒤 공백은 무시한다`() {
        assertThat(RouteType.of("  SAFE ")).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.of("Fast")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `모르는 값은 안전 우선으로 SAFE 다`() {
        assertThat(RouteType.of(null)).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.of("")).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.of("unknown")).isEqualTo(RouteType.SAFE)
    }
}
