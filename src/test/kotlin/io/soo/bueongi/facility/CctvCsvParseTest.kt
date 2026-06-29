package io.soo.bueongi.facility

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 전국 CCTV CSV 라인 파서([parseCctvCsvLine]) 단위 테스트. 문자열 입력만으로 검증하므로
 * 외부 78MB 파일에 의존하지 않는다. 정상 18컬럼·콤마밀린 행·헤더·좌표불량 케이스를 고정한다.
 */
class CctvCsvParseTest {

    @Test
    fun `정상 18컬럼 행을 위경도와 관리기관명으로 파싱`() {
        val line = "3000000,202630000000800464,서울특별시 종로구청,서울특별시 종로구 북촌로 134-1," +
            ",생활방범,2,200,360도 전방면,30,202005,02-2148-3033,37.58774,126.9839," +
            "2026-05-18,,2026-05-19 22:58:23,2026-05-18 15:24:18"
        val point = parseCctvCsvLine(line)
        assertThat(point).isNotNull
        assertThat(point!!.type).isEqualTo(FacilityType.CCTV)
        assertThat(point.lat).isEqualTo(37.58774)
        assertThat(point.lng).isEqualTo(126.9839)
        assertThat(point.name).isEqualTo("서울특별시 종로구청")
    }

    @Test
    fun `주소에 콤마가 섞여 컬럼이 밀린 행도 뒤 오프셋으로 정확히 파싱`() {
        // 주소가 따옴표로 감싸졌지만 단순 split 으로 깨짐(콤마 내부) → 19컬럼. 뒤 6컬럼은 안정적.
        val line = "3000000,202630000000800373,서울특별시 종로구청,\"서울특별시 종로구 필운대로 68 (옥인동," +
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
        val header = "개방자치단체코드,관리번호,관리기관명,소재지도로명주소,소재지지번주소,설치목적구분," +
            "카메라대수,카메라화소수,촬영방면정보,보관일수,설치연월,관리기관전화번호,WGS84위도,WGS84경도," +
            "데이터기준일자,데이터갱신구분,데이터갱신시점,최종수정시점"
        assertThat(parseCctvCsvLine(header)).isNull()
    }

    @Test
    fun `좌표 범위 밖이면 null`() {
        val line = "3000000,id,기관,주소,,생활방범,2,200,방면,30,202005,전화,999.0,126.9839," +
            "2026-05-18,,2026-05-19 22:58:23,2026-05-18 15:24:18"
        assertThat(parseCctvCsvLine(line)).isNull()
    }

    @Test
    fun `빈 줄이나 컬럼 부족 행은 null`() {
        assertThat(parseCctvCsvLine("")).isNull()
        assertThat(parseCctvCsvLine("a,b,c")).isNull()
    }
}
