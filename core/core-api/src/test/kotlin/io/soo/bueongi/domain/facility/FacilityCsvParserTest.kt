package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.safemap.Epsg5174
import io.soo.bueongi.client.safemap.Epsg5179
import io.soo.bueongi.core.enums.FacilityType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

/**
 * [FacilityCsvParser] 파싱 검증 — 종류별 좌표계(wgs84/epsg5179/epsg5174), 좌표없음·불량·비방범 skip,
 * 위도/경도 범용 폴백, 따옴표 필드, CP949 인코딩을 고정한다.
 */
class FacilityCsvParserTest {

    private val parser = FacilityCsvParser()

    private fun bytes(text: String, charset: Charset = Charsets.UTF_8): ByteArray = text.toByteArray(charset)

    @Test
    fun `cctv wgs84 - 방범 용도만 적재하고 카메라대수·주소를 읽는다`() {
        val csv = """
            위도,경도,관리기관명,소재지지번주소,설치목적구분,카메라대수
            37.5,127.0,종로구청,서울 종로구 1,생활방범,4
            37.51,127.01,중구청,서울 중구 2,다목적,2
            37.52,127.02,교통과,서울 3,교통단속,1
            999.0,127.0,불량,서울 4,생활방범,1
        """.trimIndent()

        val result = parser.parse(bytes(csv), FacilityCsvSpec.forType(FacilityType.CCTV))

        assertThat(result.coordSystem).isEqualTo(FacilityCoordSystem.WGS84)
        assertThat(result.received).isEqualTo(4)
        assertThat(result.points).hasSize(2) // 교통단속(비방범)·좌표불량 제외
        assertThat(result.skipped).isEqualTo(2)
        val first = result.points.first()
        assertThat(first.name).isEqualTo("종로구청")
        assertThat(first.purpose).isEqualTo("생활방범")
        assertThat(first.cameraCount).isEqualTo(4)
        assertThat(first.address).isEqualTo("서울 종로구 1")
    }

    @Test
    fun `safehouse wgs84 - 업소명·도로명주소를 읽는다`() {
        val csv = """
            위도,경도,업소명,소재지도로명주소
            37.5,127.0,안심약국,서울 종로구 대로 1
        """.trimIndent()

        val result = parser.parse(bytes(csv), FacilityCsvSpec.forType(FacilityType.SAFEHOUSE))

        assertThat(result.coordSystem).isEqualTo(FacilityCoordSystem.WGS84)
        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("안심약국")
        assertThat(result.points.first().address).isEqualTo("서울 종로구 대로 1")
    }

    @Test
    fun `lamp wgs84 - 주소 헤더 폴백(소재지지번주소)을 쓴다`() {
        val csv = """
            위도,경도,관리번호,소재지지번주소
            37.5,127.0,L-001,서울 종로구 5
        """.trimIndent()

        val result = parser.parse(bytes(csv), FacilityCsvSpec.forType(FacilityType.LAMP))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("L-001")
        assertThat(result.points.first().address).isEqualTo("서울 종로구 5")
    }

    @Test
    fun `police epsg5179 - X·Y 좌표를 WGS84 로 변환해 적재한다`() {
        val (x, y) = Epsg5179.fromWgs84(37.5665, 126.9780)
        val csv = """
            X좌표,Y좌표,시설명,주소
            $x,$y,종로파출소,서울 종로구 6
        """.trimIndent()

        val result = parser.parse(bytes(csv), FacilityCsvSpec.forType(FacilityType.POLICE))

        assertThat(result.coordSystem).isEqualTo(FacilityCoordSystem.EPSG5179)
        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().lat).isCloseTo(37.5665, org.assertj.core.api.Assertions.within(1e-4))
        assertThat(result.points.first().name).isEqualTo("종로파출소")
    }

    @Test
    fun `emergency_bell epsg5174 - X·Y 좌표를 WGS84 로 변환해 적재한다`() {
        val (x, y) = Epsg5174.fromWgs84(37.4981, 127.0276)
        val csv = """
            X좌표,Y좌표,설치장소,소재지지번주소
            $x,$y,강남역 3번출구,서울 강남구 7
        """.trimIndent()

        val result = parser.parse(bytes(csv), FacilityCsvSpec.forType(FacilityType.BELL))

        assertThat(result.coordSystem).isEqualTo(FacilityCoordSystem.EPSG5174)
        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().lat).isCloseTo(37.4981, org.assertj.core.api.Assertions.within(1e-4))
        assertThat(result.points.first().name).isEqualTo("강남역 3번출구")
    }

    @Test
    fun `police 주소만 있고 좌표가 없으면 전부 skip(에러 아님)`() {
        // B-3 표준데이터 형태(관서명·도로명주소·전화번호만, 좌표 없음) → 지오코더 없어 skip.
        val csv = """
            관서명,소재지도로명주소,전화번호
            종로파출소,서울 종로구 대로 8,02-100
            중부파출소,서울 중구 대로 9,02-200
        """.trimIndent()

        val result = parser.parse(bytes(csv), FacilityCsvSpec.forType(FacilityType.POLICE))

        assertThat(result.received).isEqualTo(2)
        assertThat(result.points).isEmpty()
        assertThat(result.skipped).isEqualTo(2)
    }

    @Test
    fun `범용 폴백 - 투영 종류라도 위도·경도 헤더가 있으면 WGS84 로 읽는다`() {
        // police(기본 epsg5179)인데 표준데이터가 위도/경도를 직접 주는 경우.
        val csv = """
            위도,경도,시설명,주소
            37.5,127.0,종로치안센터,서울 종로구 10
        """.trimIndent()

        val result = parser.parse(bytes(csv), FacilityCsvSpec.forType(FacilityType.POLICE))

        assertThat(result.coordSystem).isEqualTo(FacilityCoordSystem.WGS84)
        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().lat).isEqualTo(37.5)
    }

    @Test
    fun `따옴표로 감싼 필드 안의 콤마는 하나의 값으로 읽는다`() {
        val csv = "위도,경도,업소명,소재지도로명주소\n37.5,127.0,\"안심, 편의점\",\"서울 종로구, 11\""

        val result = parser.parse(bytes(csv), FacilityCsvSpec.forType(FacilityType.SAFEHOUSE))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("안심, 편의점")
        assertThat(result.points.first().address).isEqualTo("서울 종로구, 11")
    }

    @Test
    fun `CP949 로 인코딩된 CSV 도 한글 헤더로 파싱한다`() {
        val csv = """
            위도,경도,관리기관명,소재지지번주소,설치목적구분,카메라대수
            37.5,127.0,종로구청,서울 종로구 12,생활방범,3
        """.trimIndent()

        val result = parser.parse(bytes(csv, Charset.forName("MS949")), FacilityCsvSpec.forType(FacilityType.CCTV))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("종로구청")
    }

    @Test
    fun `헤더만 있고 데이터가 없으면 received 0 points 0`() {
        val result = parser.parse(bytes("위도,경도,업소명,소재지도로명주소\n"), FacilityCsvSpec.forType(FacilityType.SAFEHOUSE))

        assertThat(result.received).isZero()
        assertThat(result.points).isEmpty()
    }

    @Test
    fun `UTF-8 BOM 이 붙어도 첫 헤더를 올바로 읽는다`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val body = "위도,경도,업소명,소재지도로명주소\n37.5,127.0,안심마트,서울 13".toByteArray(Charsets.UTF_8)

        val result = parser.parse(bom + body, FacilityCsvSpec.forType(FacilityType.SAFEHOUSE))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("안심마트")
    }
}
