package io.soo.bueongi.core.safetyspot.api.request

import io.soo.bueongi.core.safetyspot.domain.nearby.error.CoordinateOutOfRangeException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NearbySafetySpotsRequestTest {
    @Test
    fun `위도·경도가 허용 범위 밖이면 거부한다`() {
        assertThatThrownBy { NearbySafetySpotsRequest(lat = 91.0, lng = 126.90) }
            .isInstanceOf(CoordinateOutOfRangeException::class.java)
        assertThatThrownBy { NearbySafetySpotsRequest(lat = 37.55, lng = 181.0) }
            .isInstanceOf(CoordinateOutOfRangeException::class.java)
    }
}
