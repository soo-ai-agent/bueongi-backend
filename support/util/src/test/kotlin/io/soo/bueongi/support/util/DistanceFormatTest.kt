package io.soo.bueongi.support.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class DistanceFormatTest {

    @Test
    fun `formatDistance - 1km 미만은 10m 단위 m 표기`() {
        assertThat(DistanceFormat.formatDistance(0.0)).isEqualTo("0m")
        assertThat(DistanceFormat.formatDistance(854.0)).isEqualTo("850m")
        assertThat(DistanceFormat.formatDistance(857.0)).isEqualTo("860m")
        assertThat(DistanceFormat.formatDistance(999.0)).isEqualTo("1000m")
    }

    @Test
    fun `formatDistance - 1km 이상은 소수1자리 km 표기`() {
        assertThat(DistanceFormat.formatDistance(1000.0)).isEqualTo("1.0km")
        assertThat(DistanceFormat.formatDistance(1234.0)).isEqualTo("1.2km")
    }

    @Test
    fun `formatDistance - 음수는 0m 로 방어`() {
        assertThat(DistanceFormat.formatDistance(-100.0)).isEqualTo("0m")
    }

    @Test
    fun `walkMinutes - 67m per minute 환산, 최소 1분`() {
        assertThat(DistanceFormat.walkMinutes(0.0)).isEqualTo(1)
        assertThat(DistanceFormat.walkMinutes(67.0)).isEqualTo(1)
        assertThat(DistanceFormat.walkMinutes(670.0)).isEqualTo(10)
        assertThat(DistanceFormat.walkMinutes(-50.0)).isEqualTo(1)
    }
}
