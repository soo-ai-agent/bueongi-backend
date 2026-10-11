package io.soo.bueongi.core.admin.safetyspot.domain.status

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.core.admin.safetyspot.api.response.RegionCenterResponse
import io.soo.bueongi.core.admin.safetyspot.api.response.RegionSafetySpotResponse
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionNameCatalog
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionSafetySpotStatusService
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class RegionSafetySpotStatusServiceTest {
    private fun service(points: Map<SafetySpotType, List<SafetySpotPoint>>): RegionSafetySpotStatusService =
        RegionSafetySpotStatusService(FakeSafetySpotSource(points), RegionNameCatalog(jacksonObjectMapper()))

    @Test
    fun `자치단체별로 종류를 나눠 세고 많은 곳부터 보여 준다`() {
        val summary: List<RegionSafetySpotResponse> =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.50, 127.00, regionCode = MAPO),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.51, 127.01, regionCode = MAPO),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 35.54, 128.49, regionCode = CHANGNYEONG),
                        ),
                    SafetySpotType.BELL to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.BELL, 37.52, 127.02, regionCode = MAPO),
                        ),
                ),
            ).summarize()

        assertThat(summary.map { it.code }).containsExactly(MAPO, CHANGNYEONG)
        val mapo: RegionSafetySpotResponse = summary.first()
        assertThat(mapo.total).isEqualTo(3)
        assertThat(mapo.counts[SafetySpotType.CCTV.cdnType]).isEqualTo(2)
        assertThat(mapo.counts[SafetySpotType.BELL.cdnType]).isEqualTo(1)
    }

    @Test
    fun `지역 행마다 파일 출처와 API 수집 출처를 나눠 센다`() {
        // 파일 2 + API 1 — 뒤바뀌면 단언이 깨지도록 비대칭으로 둔다.
        val filePoints: List<SafetySpotPoint> =
            listOf(
                SafetySpotPoint.of(SafetySpotType.CCTV, 37.50, 127.00, regionCode = MAPO),
                SafetySpotPoint.of(SafetySpotType.CCTV, 37.52, 127.02, regionCode = MAPO),
            )
        val apiPoint: SafetySpotPoint = SafetySpotPoint.of(SafetySpotType.SECURITY_LAMP, 37.51, 127.01, regionCode = MAPO)
        val source =
            FakeSafetySpotSource(
                points = mapOf(SafetySpotType.CCTV to filePoints),
                apiPoints = mapOf(SafetySpotType.SECURITY_LAMP to listOf(apiPoint)),
            )

        val summary = RegionSafetySpotStatusService(source, RegionNameCatalog(jacksonObjectMapper())).summarize()

        val mapo: RegionSafetySpotResponse = summary.single()
        assertThat(mapo.total).isEqualTo(3)
        assertThat(mapo.fileCount).isEqualTo(2)
        assertThat(mapo.apiCount).isEqualTo(1)
    }

    @Test
    fun `이름표에 있는 코드는 사람이 읽는 이름으로 바꾼다`() {
        val summary =
            service(
                mapOf(SafetySpotType.CCTV to listOf(SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.0, regionCode = MAPO))),
            ).summarize()

        assertThat(summary.single().sido).isEqualTo("서울특별시")
        assertThat(summary.single().name).isEqualTo("마포구")
    }

    @Test
    fun `옛 시도 이름은 현재 이름으로 모은다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 38.1, 127.7, regionCode = HWACHEON),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.7, 128.8, regionCode = GANGNEUNG),
                        ),
                ),
            ).summarize()

        assertThat(summary.map { it.sido }.toSet()).containsExactly("강원특별자치도")
        assertThat(summary.map { it.name }).containsExactlyInAnyOrder("화천군", "강릉시")
    }

    @Test
    fun `모르는 코드는 코드를 그대로 보여 주고 시도는 비운다 - 추측하지 않는다`() {
        val summary =
            service(
                mapOf(SafetySpotType.CCTV to listOf(SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.0, regionCode = "9999999"))),
            ).summarize()

        assertThat(summary.single().name).isEqualTo("9999999")
        assertThat(summary.single().sido).isEmpty()
    }

    @Test
    fun `지역을 모르는 지점도 한 줄로 모아 합계를 맞춘다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.0),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.6, 127.1, regionCode = MAPO),
                        ),
                ),
            ).summarize()

        assertThat(summary.sumOf { it.total }).isEqualTo(2)
        assertThat(summary.map { it.code }).contains("unknown")
    }

    @Test
    fun `묶어 적은 광주 전남은 자치단체로 갈라 제 시도로 돌린다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 35.1, 126.9, regionCode = GWANGJU_BUKGU),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 34.8, 126.4, regionCode = MOKPO),
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
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 34.9, 127.5, regionCode = SUNCHEON),
                        ),
                ),
            ).summarize()

        assertThat(summary.single().sido).isEqualTo("전라남도")
    }

    @Test
    fun `기관명이 시도를 안 밝힌 자치단체도 소재지 기준으로 시도를 갖는다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.4, 126.9, regionCode = GEUMCHEON),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 36.6, 127.4, regionCode = CHEONGJU),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 34.7, 127.7, regionCode = YEOSU),
                        ),
                ),
            ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["금천구"]).isEqualTo("서울특별시")
        assertThat(bySido["청주시"]).isEqualTo("충청북도")
        assertThat(bySido["여수시"]).isEqualTo("전라남도")
        assertThat(summary.map { it.sido }).doesNotContain("")
    }

    @Test
    fun `행정표준코드는 앞 2자리로 시도를 채운다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.0, regionCode = "11110"),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 33.4, 126.5, regionCode = "50110"),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.8, 127.7, regionCode = "42110"),
                        ),
                ),
            ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["11110"]).isEqualTo("서울특별시")
        assertThat(bySido["50110"]).isEqualTo("제주특별자치도")
        assertThat(bySido["42110"]).isEqualTo("강원특별자치도")
    }

    @Test
    fun `묶어 쓰던 코드 블록도 광주와 전남으로 갈라 채운다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 35.19, 126.91, regionCode = "12300"),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 34.80, 126.40, regionCode = "12110"),
                        ),
                ),
            ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["12300"]).isEqualTo("광주광역시")
        assertThat(bySido["12110"]).isEqualTo("전라남도")
    }

    @Test
    fun `표준에 없는 코드는 시도를 비워 둔다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.0, regionCode = "99999"),
                        ),
                ),
            ).summarize()

        assertThat(summary.single().name).isEqualTo("99999")
        assertThat(summary.single().sido).isEmpty()
    }

    @Test
    fun `이름표에 없는 비상벨 코드도 시도는 채운다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.48, 126.58, regionCode = "3490000"),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 35.14, 126.93, regionCode = "3590000"),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 34.80, 126.40, regionCode = "4800000"),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 34.46, 126.25, regionCode = "5000000"),
                        ),
                ),
            ).summarize()

        val bySido: Map<String, String> = summary.associate { it.name to it.sido }
        assertThat(bySido["3490000"]).isEqualTo("인천광역시")
        assertThat(bySido["3590000"]).isEqualTo("광주광역시")
        assertThat(bySido["4800000"]).isEqualTo("전라남도")
        assertThat(bySido["5000000"]).isEqualTo("전라남도")
        assertThat(summary.map { it.name }).contains("3490000")
    }

    @Test
    fun `지역 중심은 그 지역 지점들의 평균 좌표다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.50, 127.00, regionCode = MAPO),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.60, 127.20, regionCode = MAPO),
                        ),
                ),
            ).summarize()

        val center: RegionCenterResponse = summary.single().center ?: error("중심 좌표가 있어야 한다")
        assertThat(center.lat).isCloseTo(37.55, within(1e-9))
        assertThat(center.lng).isCloseTo(127.10, within(1e-9))
    }

    @Test
    fun `한반도 밖 좌표는 중심 계산에서 뺀다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.50, 127.00, regionCode = MAPO),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 0.0, 0.0, regionCode = MAPO),
                            SafetySpotPoint.of(SafetySpotType.CCTV, Double.NaN, 127.0, regionCode = MAPO),
                        ),
                ),
            ).summarize()

        val row: RegionSafetySpotResponse = summary.single()
        assertThat(row.total).isEqualTo(3)
        val center: RegionCenterResponse = row.center ?: error("중심 좌표가 있어야 한다")
        assertThat(center.lat).isCloseTo(37.50, within(1e-9))
        assertThat(center.lng).isCloseTo(127.00, within(1e-9))
    }

    @Test
    fun `쓸 수 있는 좌표가 하나도 없으면 중심을 비운다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 0.0, 0.0, regionCode = MAPO),
                        ),
                ),
            ).summarize()

        assertThat(summary.single().center).isNull()
    }

    @Test
    fun `지역을 모르는 줄에는 중심 좌표를 주지 않는다`() {
        val summary =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.0, regionCode = null),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 35.1, 129.0, regionCode = null),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.56, 126.91, regionCode = MAPO),
                        ),
                ),
            ).summarize()

        val unknown = summary.single { it.code == "unknown" }
        assertThat(unknown.total).isEqualTo(2)
        assertThat(unknown.center).isNull()
        assertThat(summary.single { it.name == "마포구" }.center).isNotNull()
    }

    private companion object {
        const val MAPO: String = "3130000"

        const val HWACHEON: String = "4311000"
        const val GANGNEUNG: String = "4201000"

        const val GWANGJU_BUKGU: String = "5820000"
        const val MOKPO: String = "5780000"
        const val SUNCHEON: String = "5790000"

        const val GEUMCHEON: String = "3170000"
        const val CHEONGJU: String = "5710000"
        const val YEOSU: String = "5785000"
        const val CHANGNYEONG: String = "5410000"
    }
}
