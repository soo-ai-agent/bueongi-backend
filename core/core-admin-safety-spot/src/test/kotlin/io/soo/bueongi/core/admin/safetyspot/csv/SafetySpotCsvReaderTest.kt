package io.soo.bueongi.core.admin.safetyspot.csv

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.CellRegionCodeFinder
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotCsvReader
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.cctv.CctvSpot
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.securitylamp.SecurityLampSpot
import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.storage.db.safetyspot.store.RegionCodeFinder
import io.soo.bueongi.support.util.Epsg5174
import io.soo.bueongi.support.util.Epsg5179
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.GZIPOutputStream

class SafetySpotCsvReaderTest {
    private val reader = SafetySpotCsvReader()

    private fun bytes(text: String, charset: Charset = Charsets.UTF_8): ByteArray = text.toByteArray(charset)

    @Test
    fun `cctv wgs84 - 방범 용도만 적재하고 카메라대수·주소를 읽는다`() {
        val csv =
            """
            위도,경도,관리기관명,소재지지번주소,설치목적구분,카메라대수
            37.5,127.0,종로구청,서울 종로구 1,생활방범,4
            37.51,127.01,중구청,서울 중구 2,다목적,2
            37.52,127.02,교통과,서울 3,교통단속,1
            999.0,127.0,불량,서울 4,생활방범,1
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.CCTV))

        assertThat(result.coordSystem).isEqualTo(SafetySpotCoordSystem.WGS84)
        assertThat(result.received).isEqualTo(4)
        assertThat(result.points).hasSize(2)
        assertThat(result.skipped).isEqualTo(2)
        val first = result.points.first()
        assertThat(first.name).isEqualTo("종로구청")
        val cctv: CctvSpot = first as CctvSpot
        assertThat(cctv.purpose).isEqualTo("생활방범")
        assertThat(cctv.cameraCount).isEqualTo(4)
        assertThat(first.address).isEqualTo("서울 종로구 1")
    }

    @Test
    fun `cctv - 개방자치단체코드 헤더가 있으면 지역 코드를 채운다`() {
        val csv =
            """
            개방자치단체코드,위도,경도,관리기관명,설치목적구분
            3000000,37.5,127.0,종로구청,생활방범
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.CCTV))

        val cctv: CctvSpot = result.points.single() as CctvSpot
        assertThat(cctv.openLocalGovCode).isEqualTo("3000000")
    }

    @Test
    fun `safehouse wgs84 - 업소명·도로명주소를 읽는다`() {
        val csv =
            """
            위도,경도,업소명,소재지도로명주소
            37.5,127.0,안심약국,서울 종로구 대로 1
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.SAFEHOUSE))

        assertThat(result.coordSystem).isEqualTo(SafetySpotCoordSystem.WGS84)
        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("안심약국")
        assertThat(result.points.first().address).isEqualTo("서울 종로구 대로 1")
    }

    @Test
    fun `lamp wgs84 - 주소 헤더 폴백(소재지지번주소)을 쓴다`() {
        val csv =
            """
            위도,경도,관리번호,소재지지번주소
            37.5,127.0,L-001,서울 종로구 5
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.STREET_LAMP))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("L-001")
        assertThat(result.points.first().address).isEqualTo("서울 종로구 5")
    }

    @Test
    fun `police epsg5179 - X·Y 좌표를 WGS84 로 변환해 적재한다`() {
        val (x, y) = Epsg5179.fromWgs84(37.5665, 126.9780)
        val csv =
            """
            X좌표,Y좌표,시설명,주소
            $x,$y,종로파출소,서울 종로구 6
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.POLICE))

        assertThat(result.coordSystem).isEqualTo(SafetySpotCoordSystem.EPSG5179)
        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().lat).isCloseTo(37.5665, org.assertj.core.api.Assertions.within(1e-4))
        assertThat(result.points.first().name).isEqualTo("종로파출소")
    }

    @Test
    fun `emergency_bell epsg5174 - X·Y 좌표를 WGS84 로 변환해 적재한다`() {
        val (x, y) = Epsg5174.fromWgs84(37.4981, 127.0276)
        val csv =
            """
            X좌표,Y좌표,설치장소,소재지지번주소
            $x,$y,강남역 3번출구,서울 강남구 7
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.BELL))

        assertThat(result.coordSystem).isEqualTo(SafetySpotCoordSystem.EPSG5174)
        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().lat).isCloseTo(37.4981, org.assertj.core.api.Assertions.within(1e-4))
        assertThat(result.points.first().name).isEqualTo("강남역 3번출구")
    }

    @Test
    fun `공공데이터포털 비상벨 원본 헤더(WGS84위도·설치위치)를 읽어 적재한다`() {
        val csv =
            """
            개방자치단체코드,설치위치,소재지지번주소,WGS84위도,WGS84경도
            3000000,서울시 종로구 보건소 화장실,서울특별시 종로구 옥인동 45-30,37.5732,127.0016
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.BELL))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("서울시 종로구 보건소 화장실")
    }

    @Test
    fun `지역 코드 칸이 없는 보안등 CSV 는 좌표로 찾은 지역 코드를 붙인다`() {
        val csv =
            """
            보안등위치명,소재지지번주소,위도,경도
            개천면0132,경상남도 고성군 개천면 가천리 1000-2,35.129938,128.2775089937
            """.trimIndent()

        val nearbyCctv: SafetySpotPoint = SafetySpotPoint.of(SafetySpotType.CCTV, lat = 35.13, lng = 128.2775, regionCode = "5340000")
        val source = FakeSafetySpotSource(mapOf(SafetySpotType.CCTV to listOf(nearbyCctv)))
        val cellRegionCodeFinder = CellRegionCodeFinder(RegionCodeFinder(source))

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.SECURITY_LAMP), cellRegionCodeFinder)

        assertThat(result.points.single().regionCodeOrNull()).isEqualTo("5340000")
    }

    @Test
    fun `police 주소만 있고 좌표가 없으면 전부 skip(에러 아님)`() {
        val csv =
            """
            관서명,소재지도로명주소,전화번호
            종로파출소,서울 종로구 대로 8,02-100
            중부파출소,서울 중구 대로 9,02-200
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.POLICE))

        assertThat(result.received).isEqualTo(2)
        assertThat(result.points).isEmpty()
        assertThat(result.skipped).isEqualTo(2)
    }

    @Test
    fun `범용 폴백 - 투영 종류라도 위도·경도 헤더가 있으면 WGS84 로 읽는다`() {
        val csv =
            """
            위도,경도,시설명,주소
            37.5,127.0,종로치안센터,서울 종로구 10
            """.trimIndent()

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.POLICE))

        assertThat(result.coordSystem).isEqualTo(SafetySpotCoordSystem.WGS84)
        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().lat).isEqualTo(37.5)
    }

    @Test
    fun `따옴표로 감싼 필드 안의 콤마는 하나의 값으로 읽는다`() {
        val csv: String = "위도,경도,업소명,소재지도로명주소\n37.5,127.0,\"안심, 편의점\",\"서울 종로구, 11\""

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.SAFEHOUSE))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("안심, 편의점")
        assertThat(result.points.first().address).isEqualTo("서울 종로구, 11")
    }

    @Test
    fun `줄 끝이 CRLF 여도 행 수와 마지막 칸이 그대로다`() {
        val csv: String = "위도,경도,업소명,소재지도로명주소\r\n37.5,127.0,가게1,서울 종로구 11\r\n37.6,127.1,가게2,서울 종로구 12\r\n"

        val page = reader.readPage(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.SAFEHOUSE), 0, 50, false, "")

        assertThat(page.totalRows).isEqualTo(2)
        assertThat(page.rows.first().cells.last()).isEqualTo("서울 종로구 11")
    }

    @Test
    fun `따옴표로 감싼 필드 안의 겹따옴표는 따옴표 하나로 읽는다`() {
        val csv: String = "위도,경도,업소명,소재지도로명주소\n37.5,127.0,\"안심 \"\"24시\"\" 편의점\",서울 종로구 11"

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.SAFEHOUSE))

        assertThat(result.points.single().name).isEqualTo("안심 \"24시\" 편의점")
    }

    @Test
    fun `CP949 로 인코딩된 CSV 도 한글 헤더로 파싱한다`() {
        val csv =
            """
            위도,경도,관리기관명,소재지지번주소,설치목적구분,카메라대수
            37.5,127.0,종로구청,서울 종로구 12,생활방범,3
            """.trimIndent()

        val result = reader.readPoints(bytes(csv, Charset.forName("MS949")), SafetySpotCsvFormat.forType(SafetySpotType.CCTV))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("종로구청")
    }

    @Test
    fun `헤더만 있고 데이터가 없으면 received 0 points 0`() {
        val result = reader.readPoints(bytes("위도,경도,업소명,소재지도로명주소\n"), SafetySpotCsvFormat.forType(SafetySpotType.SAFEHOUSE))

        assertThat(result.received).isZero()
        assertThat(result.points).isEmpty()
    }

    @Test
    fun `UTF-8 BOM 이 붙어도 첫 헤더를 올바로 읽는다`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val body = "위도,경도,업소명,소재지도로명주소\n37.5,127.0,안심마트,서울 13".toByteArray(Charsets.UTF_8)

        val result = reader.readPoints(bom + body, SafetySpotCsvFormat.forType(SafetySpotType.SAFEHOUSE))

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().name).isEqualTo("안심마트")
    }

    @Test
    fun `CCTV 표준데이터 머리글(WGS84위도)과 CP949 gz 압축을 읽는다`() {
        val csv = "WGS84위도,WGS84경도,관리기관명,설치목적구분\n37.5,127.0,종로구청,생활방범\n"
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { stream -> stream.write(csv.toByteArray(Charset.forName("MS949"))) }

        val result = reader.readPoints(buffer.toByteArray(), SafetySpotCsvFormat.forType(SafetySpotType.CCTV))

        assertThat(result.points).hasSize(1)
        val point = result.points.single() as CctvSpot
        assertThat(point.lat).isEqualTo(37.5)
        assertThat(point.name).isEqualTo("종로구청")
    }

    @Test
    fun `보안등 표준데이터의 보안등위치명을 이름으로 읽는다`() {
        val csv = "보안등위치명,위도,경도,제공기관코드\n길끝,37.5,127.0,3000000\n"

        val result = reader.readPoints(bytes(csv), SafetySpotCsvFormat.forType(SafetySpotType.SECURITY_LAMP))

        val point = result.points.single() as SecurityLampSpot
        assertThat(point.name).isEqualTo("길끝")
        assertThat(point.openLocalGovCode).isEqualTo("3000000")
    }
}
