package io.soo.bueongi.storage.db.core.safetyspot
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.store.parseCctvCsvLine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CctvCsvParseTest {
    @Test
    fun `정상 18컬럼 행을 위경도와 관리기관명으로 파싱`() {
        val line =
            "3000000,202630000000800464,서울특별시 종로구청,서울특별시 종로구 북촌로 134-1," +
                ",생활방범,2,200,360도 전방면,30,202005,02-2148-3033,37.58774,126.9839," +
                "2026-05-18,,2026-05-19 22:58:23,2026-05-18 15:24:18"
        val point = parseCctvCsvLine(line)
        assertThat(point).isNotNull
        assertThat(point!!.type).isEqualTo(SafetySpotType.CCTV)
        assertThat(point.lat).isEqualTo(37.58774)
        assertThat(point.lng).isEqualTo(126.9839)
        assertThat(point.name).isEqualTo("서울특별시 종로구청")
        assertThat(point.purpose).isEqualTo("생활방범")
        assertThat(point.cameraCount).isEqualTo(2)
    }

    @Test
    fun `주소에 콤마가 섞여 컬럼이 밀린 행도 뒤 오프셋으로 정확히 파싱`() {
        val line =
            "3000000,202630000000800373,서울특별시 종로구청,\"서울특별시 종로구 필운대로 68 (옥인동," +
                " 옥인아파트)\",,생활방범,3,200,360도 전방면,30,202403,02-2148-3033,37.58222,126.9683," +
                "2026-05-18,,2026-05-19 22:58:21,2026-05-18 15:24:18"
        val point = parseCctvCsvLine(line)
        assertThat(point).isNotNull
        assertThat(point!!.lat).isEqualTo(37.58222)
        assertThat(point.lng).isEqualTo(126.9683)
        assertThat(point.name).isEqualTo("서울특별시 종로구청")
    }

    @Test
    fun `헤더 행은 위경도가 숫자가 아니므로 null`() {
        val header =
            "개방자치단체코드,관리번호,관리기관명,소재지도로명주소,소재지지번주소,설치목적구분," +
                "카메라대수,카메라화소수,촬영방면정보,보관일수,설치연월,관리기관전화번호,WGS84위도,WGS84경도," +
                "데이터기준일자,데이터갱신구분,데이터갱신시점,최종수정시점"
        assertThat(parseCctvCsvLine(header)).isNull()
    }

    @Test
    fun `좌표 범위 밖이면 null`() {
        val line =
            "3000000,id,기관,주소,,생활방범,2,200,방면,30,202005,전화,999.0,126.9839," +
                "2026-05-18,,2026-05-19 22:58:23,2026-05-18 15:24:18"
        assertThat(parseCctvCsvLine(line)).isNull()
    }

    @Test
    fun `빈 줄이나 컬럼 부족 행은 null`() {
        assertThat(parseCctvCsvLine("")).isNull()
        assertThat(parseCctvCsvLine("a,b,c")).isNull()
    }

    @Test
    fun `비방범 설치목적(교통단속·쓰레기단속 등)은 적재 제외(null)`() {
        val tail =
            "2,200,360도 전방면,30,202005,02-0000-0000,37.5,127.0," +
                "2026-05-18,,2026-05-19 22:58:23,2026-05-18 15:24:18"
        for (purpose in listOf("교통단속", "시설물관리", "재난재해", "쓰레기단속", "교통정보수집", "기타")) {
            val line: String = "3000000,id,기관,도로명주소,,$purpose,$tail"
            assertThat(parseCctvCsvLine(line))
                .describedAs("설치목적=%s 은 비방범이므로 제외되어야 한다", purpose)
                .isNull()
        }
    }

    @Test
    fun `방범 성격 설치목적(생활방범·다목적·어린이보호·차량방범)은 적재`() {
        val tail =
            "2,200,360도 전방면,30,202005,02-0000-0000,37.5,127.0," +
                "2026-05-18,,2026-05-19 22:58:23,2026-05-18 15:24:18"
        for (purpose in listOf("생활방범", "다목적", "어린이보호", "차량방범")) {
            val line: String = "3000000,id,기관,도로명주소,,$purpose,$tail"
            val point = parseCctvCsvLine(line)
            assertThat(point)
                .describedAs("설치목적=%s 은 방범 성격이므로 적재되어야 한다", purpose)
                .isNotNull
            assertThat(point!!.type).isEqualTo(SafetySpotType.CCTV)
        }
    }
}
