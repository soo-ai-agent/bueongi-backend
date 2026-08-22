package io.soo.bueongi.core.safetyspot.domain.status

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.core.safetyspot.domain.status.service.RegionNameCatalog
import io.soo.bueongi.core.safetyspot.domain.status.service.RegionSafetySpotStatusService
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegionSafetySpotStatusServiceTest {

    private fun service(points: Map<SafetySpotType, List<SafetySpotPoint>>): RegionSafetySpotStatusService =
        RegionSafetySpotStatusService(FakeSafetySpotSource(points), RegionNameCatalog(jacksonObjectMapper()))

    @Test
    fun `자치단체별로 종류를 나눠 세고 많은 곳부터 보여 준다`() {
        val summary: List<RegionSafetySpotCount> = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 37.50, 127.00, sigungu = MAPO),
                    SafetySpotPoint(SafetySpotType.CCTV, 37.51, 127.01, sigungu = MAPO),
                    SafetySpotPoint(SafetySpotType.CCTV, 35.54, 128.49, sigungu = CHANGNYEONG),
                ),
                SafetySpotType.BELL to listOf(
                    SafetySpotPoint(SafetySpotType.BELL, 37.52, 127.02, sigungu = MAPO),
                ),
            ),
        ).summarize()

        assertThat(summary.map { it.code }).containsExactly(MAPO, CHANGNYEONG)
        val mapo: RegionSafetySpotCount = summary.first()
        assertThat(mapo.total).isEqualTo(3)
        assertThat(mapo.countsByType[SafetySpotType.CCTV]).isEqualTo(2)
        assertThat(mapo.countsByType[SafetySpotType.BELL]).isEqualTo(1)
    }

    @Test
    fun `이름표에 있는 코드는 사람이 읽는 이름으로 바꾼다`() {
        val summary = service(
            mapOf(SafetySpotType.CCTV to listOf(SafetySpotPoint(SafetySpotType.CCTV, 37.5, 127.0, sigungu = MAPO))),
        ).summarize()

        assertThat(summary.single().sido).isEqualTo("서울특별시")
        assertThat(summary.single().name).isEqualTo("마포구")
    }

    /**
     * 원천 CSV 가 기관마다 갱신 시점이 달라 화천군만 옛 이름('강원도')으로 남아 있었다.
     * 그대로 두면 같은 도가 화면에서 두 줄로 갈린다.
     */
    @Test
    fun `옛 시도 이름은 현재 이름으로 모은다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 38.1, 127.7, sigungu = HWACHEON),
                    SafetySpotPoint(SafetySpotType.CCTV, 37.7, 128.8, sigungu = GANGNEUNG),
                ),
            ),
        ).summarize()

        assertThat(summary.map { it.sido }.toSet()).containsExactly("강원특별자치도")
        assertThat(summary.map { it.name }).containsExactlyInAnyOrder("화천군", "강릉시")
    }

    @Test
    fun `모르는 코드는 코드를 그대로 보여 주고 시도는 비운다 - 추측하지 않는다`() {
        val summary = service(
            mapOf(SafetySpotType.CCTV to listOf(SafetySpotPoint(SafetySpotType.CCTV, 37.5, 127.0, sigungu = "9999999"))),
        ).summarize()

        assertThat(summary.single().name).isEqualTo("9999999")
        assertThat(summary.single().sido).isEmpty()
    }

    /** 빼 버리면 합계가 전체 적재량과 안 맞아 "왜 적은가"를 다시 조사하게 된다. */
    @Test
    fun `지역을 모르는 지점도 한 줄로 모아 합계를 맞춘다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 37.5, 127.0),
                    SafetySpotPoint(SafetySpotType.CCTV, 37.6, 127.1, sigungu = MAPO),
                ),
            ),
        ).summarize()

        assertThat(summary.sumOf { it.total }).isEqualTo(2)
        assertThat(summary.map { it.code }).contains("unknown")
    }

    /**
     * '전남광주통합특별시' 는 통합 논의 때 쓰던 이름이라 현행 법정 시도가 아니다.
     * 개명이 아니라 두 시도를 묶은 것이라, 이름표만 바꿔서는 못 풀고 자치단체로 갈라야 한다.
     */
    @Test
    fun `묶어 적은 광주 전남은 자치단체로 갈라 제 시도로 돌린다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 35.1, 126.9, sigungu = GWANGJU_BUKGU),
                    SafetySpotPoint(SafetySpotType.CCTV, 34.8, 126.4, sigungu = MOKPO),
                ),
            ),
        ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["북구"]).isEqualTo("광주광역시")
        assertThat(bySido["목포시"]).isEqualTo("전라남도")
        assertThat(summary.map { it.sido }).doesNotContain("전남광주통합특별시")
    }

    @Test
    fun `묶어 적은 이름은 어느 줄에도 남지 않는다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 34.9, 127.5, sigungu = SUNCHEON),
                ),
            ),
        ).summarize()

        assertThat(summary.single().sido).isEqualTo("전라남도")
    }

    /**
     * 원천 기관명이 부서명이거나("평생교육과") 시도를 안 붙인 자치단체가 34곳 있었다.
     * 소재지 주소가 시도를 말해 주므로 그걸로 채웠다 — 이름으로 짐작한 것이 아니다.
     */
    @Test
    fun `기관명이 시도를 안 밝힌 자치단체도 소재지 기준으로 시도를 갖는다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 37.4, 126.9, sigungu = GEUMCHEON),
                    SafetySpotPoint(SafetySpotType.CCTV, 36.6, 127.4, sigungu = CHEONGJU),
                    SafetySpotPoint(SafetySpotType.CCTV, 34.7, 127.7, sigungu = YEOSU),
                ),
            ),
        ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["금천구"]).isEqualTo("서울특별시")
        assertThat(bySido["청주시"]).isEqualTo("충청북도")
        // 여수는 주소마저 묶인 이름으로 와서, 채우기와 갈라내기가 이어 붙어야 제자리에 온다.
        assertThat(bySido["여수시"]).isEqualTo("전라남도")
        assertThat(summary.map { it.sido }).doesNotContain("")
    }

    /**
     * 재난안전데이터·생활안전지도는 5자리 행정표준코드(시군구코드)를 준다 — 이름표가 쓰는
     * 7자리 개방자치단체코드와 다른 체계라 표에 없었고, 284곳이 시도 없이 코드만 떠 있었다.
     * 표준이 앞 2자리를 시도로 정해 놓았으니 이름은 몰라도 시도는 채운다.
     */
    @Test
    fun `행정표준코드는 앞 2자리로 시도를 채운다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 37.5, 127.0, sigungu = "11110"),
                    SafetySpotPoint(SafetySpotType.CCTV, 33.4, 126.5, sigungu = "50110"),
                    SafetySpotPoint(SafetySpotType.CCTV, 37.8, 127.7, sigungu = "42110"),
                ),
            ),
        ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["11110"]).isEqualTo("서울특별시")
        assertThat(bySido["50110"]).isEqualTo("제주특별자치도")
        // 강원은 특별자치도로 바뀌며 코드가 42→51 로 늘었다. 옛 코드도 같은 시도로 본다.
        assertThat(bySido["42110"]).isEqualTo("강원특별자치도")
    }

    /**
     * `12` 는 표준에 없는 코드인데, 광주·전남을 묶어 쓰던 블록이다(27개 = 전남 22시군 + 광주 5구).
     * 좌표 중심이 광주 안인 다섯 개만 광주로 돌린다.
     */
    @Test
    fun `묶어 쓰던 코드 블록도 광주와 전남으로 갈라 채운다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 35.19, 126.91, sigungu = "12300"),
                    SafetySpotPoint(SafetySpotType.CCTV, 34.80, 126.40, sigungu = "12110"),
                ),
            ),
        ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["12300"]).isEqualTo("광주광역시")
        assertThat(bySido["12110"]).isEqualTo("전라남도")
    }

    /** 모르는 코드까지 억지로 채우지 않는다 — 표준에 없는 시도 번호는 비워 둔다. */
    @Test
    fun `표준에 없는 코드는 시도를 비워 둔다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 37.5, 127.0, sigungu = "99999"),
                ),
            ),
        ).summarize()

        assertThat(summary.single().name).isEqualTo("99999")
        assertThat(summary.single().sido).isEmpty()
    }

    /**
     * 비상벨 원천만 쓰는 7자리 코드 29개가 이름표에도 표준코드에도 없어 시도 없이 떠 있었다.
     * 7자리는 접두로 시도를 못 가려(2자리가 여러 시도에 걸친다) 지점 좌표로 확인해 적었다.
     */
    @Test
    fun `이름표에 없는 비상벨 코드도 시도는 채운다`() {
        val summary = service(
            mapOf(
                SafetySpotType.CCTV to listOf(
                    SafetySpotPoint(SafetySpotType.CCTV, 37.48, 126.58, sigungu = "3490000"),
                    SafetySpotPoint(SafetySpotType.CCTV, 35.14, 126.93, sigungu = "3590000"),
                    SafetySpotPoint(SafetySpotType.CCTV, 34.80, 126.40, sigungu = "4800000"),
                    SafetySpotPoint(SafetySpotType.CCTV, 34.46, 126.25, sigungu = "5000000"),
                ),
            ),
        ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["3490000"]).isEqualTo("인천광역시")
        assertThat(bySido["3590000"]).isEqualTo("광주광역시")
        assertThat(bySido["4800000"]).isEqualTo("전라남도")
        assertThat(bySido["5000000"]).isEqualTo("전라남도")
        // 자치단체 이름은 원천이 주지 않아 코드를 그대로 보여 준다.
        assertThat(summary.map { it.name }).contains("3490000")
    }

    private companion object {
        const val MAPO: String = "3130000"

        /** 이름표에 옛 이름('강원도')으로 남아 있던 유일한 자치단체. */
        const val HWACHEON: String = "4311000"
        const val GANGNEUNG: String = "4201000"

        /** 원천이 '전남광주통합특별시' 로 묶어 적어 둔 자치단체들. */
        const val GWANGJU_BUKGU: String = "5820000"
        const val MOKPO: String = "5780000"
        const val SUNCHEON: String = "5790000"

        /** 원천 기관명이 시도를 안 밝혀 '지역 미상' 이던 자치단체들. */
        const val GEUMCHEON: String = "3170000"
        const val CHEONGJU: String = "5710000"
        const val YEOSU: String = "5785000"
        const val CHANGNYEONG: String = "5410000"
    }
}