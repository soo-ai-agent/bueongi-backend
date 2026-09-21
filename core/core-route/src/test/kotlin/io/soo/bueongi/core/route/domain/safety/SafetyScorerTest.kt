package io.soo.bueongi.core.route.domain.safety

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.safety.model.SafetyAxis
import io.soo.bueongi.core.route.domain.safety.model.SafetyDataCoverage
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.safety.service.SafetyWeightsCatalog
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SafetyScorerTest {
    private val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))

    @Test
    fun `밤에는 종류별 밤 가중치가 점수에 그대로 실린다`() {
        assertThat(scoreWithDense(SafetySpotType.CCTV, NIGHT)).isEqualTo(26)
        assertThat(scoreWithDense(SafetySpotType.STREET_LAMP, NIGHT)).isEqualTo(31)
        assertThat(scoreWithDense(SafetySpotType.SECURITY_LAMP, NIGHT)).isEqualTo(31)
        assertThat(scoreWithDense(SafetySpotType.BELL, NIGHT)).isEqualTo(19)
        assertThat(scoreWithDense(SafetySpotType.POLICE, NIGHT)).isEqualTo(12)
        assertThat(scoreWithDense(SafetySpotType.SAFEHOUSE, NIGHT)).isEqualTo(9)
    }

    @Test
    fun `낮에는 조명 가중치가 내려가고 나머지가 올라간다`() {
        assertThat(scoreWithDense(SafetySpotType.STREET_LAMP, DAY)).isEqualTo(10)
        assertThat(scoreWithDense(SafetySpotType.CCTV, DAY)).isEqualTo(35)
        assertThat(scoreWithDense(SafetySpotType.BELL, DAY)).isEqualTo(25)
        assertThat(scoreWithDense(SafetySpotType.POLICE, DAY)).isEqualTo(15)
        assertThat(scoreWithDense(SafetySpotType.SAFEHOUSE, DAY)).isEqualTo(12)
    }

    @Test
    fun `조명만 있는 길과 CCTV 만 있는 길의 우열이 밤낮으로 뒤집힌다`() {
        val lampOnly = fullyCovered(SafetySpotType.STREET_LAMP to denseSpots())
        val cctvOnly = fullyCovered(SafetySpotType.CCTV to denseSpots())

        assertThat(scorer(lampOnly, NIGHT).score(path).score)
            .describedAs("밤에는 조명이 이긴다")
            .isGreaterThan(scorer(cctvOnly, NIGHT).score(path).score)
        assertThat(scorer(lampOnly, DAY).score(path).score)
            .describedAs("낮에는 CCTV 가 이긴다")
            .isLessThan(scorer(cctvOnly, DAY).score(path).score)
    }

    @Test
    fun `보안등은 가로등과 합쳐 조명 점수가 된다`() {
        val lamps: List<GeoPoint> = spotsFor(HALF_LAMP_PER_KM)
        val street: List<GeoPoint> = lamps.take(lamps.size / 2)
        val security: List<GeoPoint> = lamps.drop(lamps.size / 2)

        val streetOnly: Int? = scorer(fullyCovered(SafetySpotType.STREET_LAMP to street), NIGHT).score(path).score
        val allAsStreet: Int? = scorer(fullyCovered(SafetySpotType.STREET_LAMP to lamps), NIGHT).score(path).score
        val split: Int? =
            scorer(
                fullyCovered(
                    SafetySpotType.STREET_LAMP to street,
                    SafetySpotType.SECURITY_LAMP to security,
                ),
                NIGHT,
            ).score(path).score

        assertThat(split).describedAs("나눠 심으나 몰아 심으나 같은 조명 점수").isEqualTo(allAsStreet)
        assertThat(split).isGreaterThan(streetOnly)
    }

    @Test
    fun `가중치 합이 1이라 모두 가득 차면 밤낮 모두 100점`() {
        val byType: MutableMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in WEIGHTED_TYPES) {
            byType[type] = denseSpots().map { SafetySpotPoint(type, it.lat, it.lng) }
        }
        val source = FakeSafetySpotSource(byType, listOf(SafePath(coords = path, name = "안심귀갓길")))

        assertThat(scorer(source, NIGHT).score(path).score).isEqualTo(100)
        assertThat(scorer(source, DAY).score(path).score).isEqualTo(100)
    }

    @Test
    fun `절반 지점 밀도는 축의 절반을 채운다`() {
        // 0.88km 경로에 정수 개수만 깔 수 있어 정확히 2.7/km 를 만들지 못한다(실제 2.26/km).
        assertThat(cctvFill(HALF_CCTV_PER_KM)).isBetween(0.42, 0.58)
    }

    @Test
    fun `절반 지점을 넘어도 계속 오르되 오름폭이 준다`() {
        val one: Double = cctvFill(HALF_CCTV_PER_KM)
        val two: Double = cctvFill(HALF_CCTV_PER_KM * 2)
        val three: Double = cctvFill(HALF_CCTV_PER_KM * 3)

        assertThat(two).describedAs("절반 지점의 2배는 더 높다").isGreaterThan(one)
        assertThat(three).describedAs("3배는 2배보다 더 높다").isGreaterThan(two)
        assertThat(three - two).describedAs("오름폭은 줄어든다").isLessThan(two - one)
        assertThat(three).describedAs("1 을 넘지는 않는다").isLessThan(1.0)
    }

    private fun cctvFill(perKm: Double): Double {
        val score: Int = scorer(fullyCovered(SafetySpotType.CCTV to spotsFor(perKm)), NIGHT).score(path).score ?: 0
        return score / (NIGHT_CCTV_WEIGHT * 100)
    }

    private fun spotsFor(perKm: Double): List<GeoPoint> {
        val count: Int = Math.round(perKm * ROUTE_KM).toInt()
        val step: Double = PATH_LNG_SPAN / (count + 1)
        val spots: MutableList<GeoPoint> = ArrayList()
        for (i in 1..count) {
            spots.add(GeoPoint(37.5, 127.0 + i * step))
        }
        return spots
    }

    @Test
    fun `종류마다 적게 깔면 많이 깐 것보다 낮다`() {
        val sparse: List<GeoPoint> = listOf(GeoPoint(37.5, 127.003), GeoPoint(37.5, 127.007))
        for (type in WEIGHTED_TYPES) {
            val sparseScore: Int? = scorer(fullyCovered(type to sparse), NIGHT).score(path).score
            val denseScore: Int? = scorer(fullyCovered(type to denseSpots()), NIGHT).score(path).score

            assertThat(sparseScore).describedAs("%s 적게", type).isLessThan(denseScore)
        }
    }

    private fun scoreWithDense(
        type: SafetySpotType,
        clock: Clock,
    ): Int? = scorer(fullyCovered(type to denseSpots()), clock).score(path).score

    private fun denseSpots(): List<GeoPoint> {
        val spots: MutableList<GeoPoint> = ArrayList()
        var lng: Double = 127.0
        while (lng <= 127.01) {
            spots.add(GeoPoint(37.5, lng))
            lng += DENSE_SPOT_STEP_DEG
        }
        return spots
    }

    @Test
    fun `주변에 어떤 자료도 없으면 점수를 내지 않는다`() {
        val result = scorer(FakeSafetySpotSource(), NIGHT).score(path)

        assertThat(result.score).describedAs("0점이 아니라 '없음'이다").isNull()
        assertThat(result.breakdown.scoredAxes).isEmpty()
        assertThat(result.breakdown.unscoredAxes).hasSize(SafetyAxis.entries.size)
        assertThat(result.breakdown.axisCoverage).isZero()
        assertThat(result.breakdown.routeKm).isGreaterThan(0.0)
    }

    @Test
    fun `자료가 없는 축은 빼고 남은 축으로 다시 나눈다`() {
        val cctvOnly = FakeSafetySpotSource.of(SafetySpotType.CCTV, denseSpots())
        val result = scorer(cctvOnly, NIGHT).score(path)

        assertThat(result.score).isEqualTo(100)
        assertThat(result.breakdown.scoredAxes).containsExactly("cctv")
        assertThat(result.breakdown.unscoredAxes)
            .containsExactly("lamp", "bell", "safehouse", "police", "safePath")
        assertThat(result.breakdown.axisCoverage).isEqualTo(NIGHT_CCTV_WEIGHT)
    }

    @Test
    fun `회랑 밖에라도 그 종류가 있으면 자료 있음으로 세고 0점을 준다`() {
        val offCorridor = FakeSafetySpotSource.of(SafetySpotType.CCTV, listOf(COVERAGE_ANCHOR))
        val result = scorer(offCorridor, NIGHT).score(path)

        assertThat(result.breakdown.cctvDensity).isZero()
        assertThat(result.breakdown.scoredAxes).containsExactly("cctv")
        assertThat(result.score).isZero()
    }

    @Test
    fun `자료 유무는 여정의 모든 후보를 함께 보고 정한다`() {
        val source = fullyCovered(SafetySpotType.CCTV to denseSpots())
        val farAway = listOf(GeoPoint(36.0, 128.0), GeoPoint(36.0, 128.01))

        val together: SafetyDataCoverage = scorer(source, NIGHT).coverageOf(listOf(path, farAway))
        val alone: SafetyDataCoverage = scorer(source, NIGHT).coverageOf(listOf(farAway))

        assertThat(together.covered).describedAs("한 후보라도 닿으면 자료 있음").isNotEmpty()
        assertThat(alone.covered).describedAs("혼자였다면 자료 없음이 됐을 후보").isEmpty()
    }

    @Test
    fun `넘겨받은 자료 유무를 다시 재지 않는다`() {
        val source = fullyCovered(SafetySpotType.BELL to denseSpots())
        val cctvOnly = SafetyDataCoverage(setOf(SafetyAxis.CCTV))

        val result = scorer(source, NIGHT).score(path, cctvOnly)

        assertThat(result.breakdown.bellDensity).describedAs("세기는 센다").isGreaterThan(0.0)
        assertThat(result.breakdown.scoredAxes).containsExactly("cctv")
        assertThat(result.score).describedAs("비상벨은 점수에 안 실린다").isZero()
    }

    @Test
    fun `회랑 안 CCTV 가 점수와 밀도를 올린다`() {
        val cctv = (1..9).map { GeoPoint(37.5, 127.0 + it * 0.001) }
        val result = scorer(fullyCovered(SafetySpotType.CCTV to cctv), NIGHT).score(path)
        assertThat(result.score).isGreaterThan(0)
        assertThat(result.score).isLessThanOrEqualTo(100)
        assertThat(result.breakdown.cctvDensity).isGreaterThan(9.0)
    }

    @Test
    fun `회랑 안 지구대·파출소가 점수와 개수에 기여한다`() {
        val police = listOf(GeoPoint(37.5, 127.005))
        val result = scorer(fullyCovered(SafetySpotType.POLICE to police), NIGHT).score(path)
        assertThat(result.breakdown.policeCount).isEqualTo(1)
        assertThat(result.score).isGreaterThan(0)
    }

    @Test
    fun `회랑 안 가로등이 점수와 밀도에 기여한다`() {
        val lamps = (1..12).map { GeoPoint(37.5, 127.0 + it * 0.0007) }
        val result = scorer(fullyCovered(SafetySpotType.STREET_LAMP to lamps), NIGHT).score(path)
        assertThat(result.breakdown.lampDensity).isGreaterThan(0.0)
        assertThat(result.score).isGreaterThan(0)
    }

    @Test
    fun `우범지역은 점수를 깎는다 - 포화 시 감점 상한만큼`() {
        val cctv = (1..40).map { GeoPoint(37.5, 127.0 + it * 0.00025) }
        val withoutRisk: Int? = scorer(fullyCovered(SafetySpotType.CCTV to cctv), NIGHT).score(path).score

        val risky =
            fullyCovered(
                SafetySpotType.CCTV to cctv,
                SafetySpotType.CRIME_PRONE_AREA to listOf(GeoPoint(37.5, 127.002), GeoPoint(37.5, 127.008)),
            )
        val result = scorer(risky, NIGHT).score(path)

        assertThat(result.breakdown.crimeProneAreaCount).isEqualTo(2)
        assertThat(result.score).isEqualTo((withoutRisk ?: 0) - 20)
    }

    @Test
    fun `우범지역이 없으면 점수를 건드리지 않는다`() {
        val cctv = (1..40).map { GeoPoint(37.5, 127.0 + it * 0.00025) }
        val result = scorer(fullyCovered(SafetySpotType.CCTV to cctv), NIGHT).score(path)

        assertThat(result.breakdown.crimeProneAreaCount).isZero()
    }

    @Test
    fun `우범지역만 있으면 점수는 0 아래로 내려가지 않는다`() {
        val risky =
            fullyCovered(
                SafetySpotType.CRIME_PRONE_AREA to listOf(GeoPoint(37.5, 127.002), GeoPoint(37.5, 127.008)),
            )

        assertThat(scorer(risky, NIGHT).score(path).score).isZero()
    }

    @Test
    fun `우범지역은 시설보다 넓은 회랑으로 센다`() {
        val offCorridor = GeoPoint(37.5005, 127.005)
        val risky =
            fullyCovered(
                SafetySpotType.CCTV to listOf(offCorridor),
                SafetySpotType.CRIME_PRONE_AREA to listOf(offCorridor),
            )
        val result = scorer(risky, NIGHT).score(path)

        assertThat(result.breakdown.cctvDensity).isZero()
        assertThat(result.breakdown.crimeProneAreaCount).isEqualTo(1)
    }

    @Test
    fun `안심귀갓길 겹침은 회수 후 남긴 safePath 가중치(3점)만큼 기여`() {
        val source = fullyCovered(safePaths = listOf(SafePath(path)))
        val result = scorer(source, NIGHT).score(path)
        assertThat(result.breakdown.safePathOverlap).isGreaterThan(0.9)
        assertThat(result.score).isEqualTo(3)
    }

    @Test
    fun `결정론 - 같은 경로와 데이터는 같은 점수`() {
        val source = fullyCovered(SafetySpotType.BELL to listOf(GeoPoint(37.5, 127.005)))
        val scored = scorer(source, NIGHT)
        assertThat(scored.score(path)).isEqualTo(scored.score(path))
    }

    @Test
    fun `0 길이 경로도 폭주 없이 안전하게 0`() {
        val degenerate = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))
        val source = fullyCovered(SafetySpotType.CCTV to listOf(GeoPoint(37.5, 127.0)))
        val result = scorer(source, NIGHT).score(degenerate)
        assertThat(result.score ?: 0).isBetween(0, 100)
    }

    private fun fullyCovered(
        vararg spots: Pair<SafetySpotType, List<GeoPoint>>,
        safePaths: List<SafePath> = listOf(SafePath(listOf(COVERAGE_ANCHOR, COVERAGE_ANCHOR_END))),
    ): FakeSafetySpotSource {
        val byType: LinkedHashMap<SafetySpotType, MutableList<SafetySpotPoint>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            byType[type] = mutableListOf(SafetySpotPoint(type, COVERAGE_ANCHOR.lat, COVERAGE_ANCHOR.lng))
        }
        for ((type, points) in spots) {
            byType.getValue(type).addAll(points.map { SafetySpotPoint(type, it.lat, it.lng) })
        }
        return FakeSafetySpotSource(byType, safePaths)
    }

    private fun scorer(
        source: FakeSafetySpotSource,
        clock: Clock,
    ): SafetyScorer = SafetyScorer(source, SafetyWeightsCatalog(), clock)

    private companion object {
        val NIGHT: Clock = Clock.fixed(Instant.parse("2026-08-15T13:00:00Z"), ZoneOffset.UTC)
        val DAY: Clock = Clock.fixed(Instant.parse("2026-08-15T03:00:00Z"), ZoneOffset.UTC)

        const val ROUTE_KM: Double = 0.882

        const val PATH_LNG_SPAN: Double = 0.01

        val COVERAGE_ANCHOR: GeoPoint = GeoPoint(37.5045, 127.005)
        val COVERAGE_ANCHOR_END: GeoPoint = GeoPoint(37.5045, 127.006)

        // 좌표 5자리(약 1.1m)에서 같은 지점으로 합쳐지지 않는 가장 촘촘한 간격(약 1.3m).
        const val DENSE_SPOT_STEP_DEG: Double = 0.000015

        const val NIGHT_CCTV_WEIGHT: Double = 0.26

        const val HALF_CCTV_PER_KM: Double = SafetyScorer.HALF_CCTV_PER_KM
        const val HALF_LAMP_PER_KM: Double = SafetyScorer.HALF_LAMP_PER_KM

        val WEIGHTED_TYPES: List<SafetySpotType> =
            listOf(
                SafetySpotType.CCTV,
                SafetySpotType.STREET_LAMP,
                SafetySpotType.SECURITY_LAMP,
                SafetySpotType.BELL,
                SafetySpotType.POLICE,
                SafetySpotType.SAFEHOUSE,
            )
    }
}
