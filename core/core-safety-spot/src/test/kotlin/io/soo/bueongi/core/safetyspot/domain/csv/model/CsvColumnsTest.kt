package io.soo.bueongi.core.safetyspot.domain.csv.model

import io.soo.bueongi.core.enums.SafetySpotCoordSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CsvColumnsTest {
    private val columns = CsvColumns(
        useWgs84 = true,
        coordSystem = SafetySpotCoordSystem.WGS84,
        latIndex = 0,
        lngIndex = 1,
        xIndex = null,
        yIndex = null,
        nameIndex = 2,
        addressIndex = null,
        purposeIndex = null,
        cameraCountIndex = 3,
        phoneIndex = null,
    )

    @Test
    fun `정상 행은 값을 그대로 읽는다`() {
        val row = listOf("37.5", "127.0", "종로구청", "4")

        assertThat(columns.latitude(row)).isEqualTo(37.5)
        assertThat(columns.longitude(row)).isEqualTo(127.0)
        assertThat(columns.name(row)).isEqualTo("종로구청")
        assertThat(columns.cameraCount(row)).isEqualTo(4)
    }

    @Test
    fun `카메라 대수 0 과 음수는 없음으로 본다 - 원천이 빈칸 대신 0 을 넣는다`() {
        assertThat(columns.cameraCount(listOf("37.5", "127.0", "가", "0"))).isNull()
        assertThat(columns.cameraCount(listOf("37.5", "127.0", "가", "-1"))).isNull()
        assertThat(columns.cameraCount(listOf("37.5", "127.0", "가", "1"))).isEqualTo(1)
    }

    @Test
    fun `빈칸과 공백만 있는 칸은 없음이다`() {
        assertThat(columns.name(listOf("37.5", "127.0", "", "1"))).isNull()
        assertThat(columns.name(listOf("37.5", "127.0", "   ", "1"))).isNull()
        assertThat(columns.name(listOf("37.5", "127.0", "  종로구청  ", "1"))).isEqualTo("종로구청")
    }

    @Test
    fun `행이 짧아 컬럼이 없으면 없음이다 - 마지막 칸이 잘린 행이 흔하다`() {
        val short = listOf("37.5", "127.0")

        assertThat(columns.name(short)).isNull()
        assertThat(columns.cameraCount(short)).isNull()
        assertThat(columns.longitude(short)).isEqualTo(127.0)
    }

    @Test
    fun `헤더에 없던 컬럼(index null)은 언제나 없음이다`() {
        val row = listOf("37.5", "127.0", "가", "1")

        assertThat(columns.address(row)).isNull()
        assertThat(columns.phone(row)).isNull()
        assertThat(columns.projectedX(row)).isNull()
    }

    @Test
    fun `숫자가 아닌 값은 없음으로 본다`() {
        assertThat(columns.latitude(listOf("없음", "127.0", "가", "1"))).isNull()
        assertThat(columns.cameraCount(listOf("37.5", "127.0", "가", "네대"))).isNull()
    }
}
