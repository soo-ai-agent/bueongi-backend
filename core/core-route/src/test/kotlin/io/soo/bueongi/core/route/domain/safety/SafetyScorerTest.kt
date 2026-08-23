package io.soo.bueongi.core.route.domain.safety

import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.safety.service.SafetyWeightsCatalog
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SafetyScorerTest {

    private val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))

    /**
     * 한 종류만 포화시키면 점수가 그 종류의 가중치 × 100 이 된다.
     * 가중치를 바꾸면 경로 순위가 통째로 흔들리는데, 지금까지 `score > 0` 만 보고 있어
     * 0.30 을 0.99 로 바꿔도 아무 테스트도 깨지지 않았다.
     */
    @Test
    fun `밤에는 종류별 밤 가중치가 점수에 그대로 실린다`() {
        assertThat(scoreWithSaturated(SafetySpotType.CCTV, NIGHT)).isEqualTo(26)
        assertThat(scoreWithSaturated(SafetySpotType.STREET_LAMP, NIGHT)).isEqualTo(31)
        assertThat(scoreWithSaturated(SafetySpotType.SECURITY_LAMP, NIGHT)).isEqualTo(31)
        assertThat(scoreWithSaturated(SafetySpotType.BELL, NIGHT)).isEqualTo(19)
        assertThat(scoreWithSaturated(SafetySpotType.POLICE, NIGHT)).isEqualTo(12)
        assertThat(scoreWithSaturated(SafetySpotType.SAFEHOUSE, NIGHT)).isEqualTo(9)
    }

    /** 낮에는 조명이 거의 의미가 없다 — 그 몫이 시각과 무관한 축으로 옮겨 간다. */
    @Test
    fun `낮에는 조명 가중치가 내려가고 나머지가 올라간다`() {
        assertThat(scoreWithSaturated(SafetySpotType.STREET_LAMP, DAY)).isEqualTo(10)
        assertThat(scoreWithSaturated(SafetySpotType.CCTV, DAY)).isEqualTo(35)
        assertThat(scoreWithSaturated(SafetySpotType.BELL, DAY)).isEqualTo(25)
        assertThat(scoreWithSaturated(SafetySpotType.POLICE, DAY)).isEqualTo(15)
        assertThat(scoreWithSaturated(SafetySpotType.SAFEHOUSE, DAY)).isEqualTo(12)
    }

    /**
     * 같은 길인데 밤에는 조명이, 낮에는 CCTV 가 순위를 끌어야 한다.
     * 시각을 무시하고 한 벌의 가중치만 쓰면 두 단언이 같은 값이 되어 여기서 깨진다.
     */
    @Test
    fun `조명만 있는 길과 CCTV 만 있는 길의 우열이 밤낮으로 뒤집힌다`() {
        val lampOnly = FakeSafetySpotSource.of(SafetySpotType.STREET_LAMP, saturatingSpots(SafetySpotType.STREET_LAMP))
        val cctvOnly = FakeSafetySpotSource.of(SafetySpotType.CCTV, saturatingSpots(SafetySpotType.CCTV))

        assertThat(scorer(lampOnly, NIGHT).score(path).score)
            .describedAs("밤에는 조명이 이긴다")
            .isGreaterThan(scorer(cctvOnly, NIGHT).score(path).score)
        assertThat(scorer(lampOnly, DAY).score(path).score)
            .describedAs("낮에는 CCTV 가 이긴다")
            .isLessThan(scorer(cctvOnly, DAY).score(path).score)
    }

    /**
     * 보안등은 가로등과 같은 조명 점수에 **합산**된다(관리 주체만 다른 같은 조명).
     * 지금까지 보안등은 점수 쪽 테스트가 하나도 없어, 합산을 통째로 빼도 아무 데서도 안 깨졌다.
     */
    @Test
    fun `보안등은 가로등과 합쳐 조명 점수가 된다`() {
        val lamps: List<GeoPoint> = saturatingSpots(SafetySpotType.STREET_LAMP)
        val street: List<GeoPoint> = lamps.take(lamps.size / 2)
        val security: List<GeoPoint> = lamps.drop(lamps.size / 2)

        val streetOnly: Int = scorer(FakeSafetySpotSource.of(SafetySpotType.STREET_LAMP, street), NIGHT).score(path).score
        val bothSource = FakeSafetySpotSource(
            mapOf(
                SafetySpotType.STREET_LAMP to street.map { SafetySpotPoint(SafetySpotType.STREET_LAMP, it.lat, it.lng) },
                SafetySpotType.SECURITY_LAMP to
                    security.map { SafetySpotPoint(SafetySpotType.SECURITY_LAMP, it.lat, it.lng) },
            ),
        )
        val both: Int = scorer(bothSource, NIGHT).score(path).score

        // 절반만으로는 포화에 못 미치고(18), 보안등을 더해 16개를 채우면 조명 가중치를 다 받는다(31).
        assertThat(streetOnly).isEqualTo(18)
        assertThat(both).isEqualTo(31)
    }

    @Test
    fun `가중치 합이 1이라 모두 포화되면 밤낮 모두 100점`() {
        val byType: MutableMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in WEIGHTED_TYPES) {
            byType[type] = saturatingSpots(type).map { SafetySpotPoint(type, it.lat, it.lng) }
        }
        val source = FakeSafetySpotSource(byType, listOf(SafePath(coords = path, name = "안심귀갓길")))

        assertThat(scorer(source, NIGHT).score(path).score).isEqualTo(100)
        assertThat(scorer(source, DAY).score(path).score).isEqualTo(100)
    }

    /**
     * 포화 기준 아래에서는 점수가 개수에 비례한다. 위 테스트는 포화 이상만 보므로
     * 기준값을 낮춰도 결과가 같아 `SAT_*` 변이를 잡지 못한다 — 이 자리가 그것을 잡는다.
     */
    @Test
    fun `포화 기준 아래에서는 개수에 비례해 오른다`() {
        val two: List<GeoPoint> = saturatingSpots(SafetySpotType.CCTV).take(2)
        val four: List<GeoPoint> = saturatingSpots(SafetySpotType.CCTV).take(4)

        val twoScore: Int = scorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, two), NIGHT).score(path).score
        val fourScore: Int = scorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, four), NIGHT).score(path).score

        // 0.88km 경로에 2개 = 2.27/km, 상한 10/km 의 23% → 밤 CCTV 가중치 26점 중 6점.
        assertThat(twoScore).isEqualTo(6)
        assertThat(fourScore).isEqualTo(12)
    }

    /**
     * 상한이 실측보다 높으면 실제 경로들이 축의 바닥에만 몰려 순위를 못 가른다.
     * 서울 실측 중앙값(CCTV 2.82/km · 조명 데이터가 깔린 구간 14.8/km) 이 축의 한복판에 오는지 본다 —
     * 상한을 예전 값(20·40)으로 되돌리면 채움이 15% 아래로 떨어져 여기서 깨진다.
     */
    @Test
    fun `실측 중앙값이 축의 바닥에 깔리지 않는다`() {
        val cctvFill: Double = fillRatio(SafetySpotType.CCTV, MEASURED_CCTV_MEDIAN_PER_KM)
        val lampFill: Double = fillRatio(SafetySpotType.STREET_LAMP, MEASURED_LAMP_TYPICAL_PER_KM)

        assertThat(cctvFill).isBetween(0.2, 0.9)
        assertThat(lampFill).isBetween(0.5, 1.0)
    }

    /**
     * 그 종류만 채웠을 때 실측 밀도가 축을 얼마나 채우는가.
     * 분모는 상한이 어떻게 바뀌든 반드시 포화하는 개수로 잰다 — 상한을 되돌리면 분자만 내려가야
     * 이 비율이 뜻을 갖는다(분모까지 같이 내려가면 변이를 못 잡는다).
     */
    private fun fillRatio(type: SafetySpotType, perKm: Double): Double {
        val saturated: Int = scorer(FakeSafetySpotSource.of(type, spotsFor(ALWAYS_SATURATING_PER_KM)), NIGHT)
            .score(path).score
        val measured: Int = scorer(FakeSafetySpotSource.of(type, spotsFor(perKm)), NIGHT).score(path).score
        return measured.toDouble() / saturated
    }

    /** 그 밀도가 되도록 경로 위에 고르게 깐다 — 경로 끝을 넘어가면 회랑 밖이라 세지 않는다. */
    private fun spotsFor(perKm: Double): List<GeoPoint> {
        val count: Int = Math.round(perKm * ROUTE_KM).toInt()
        val step: Double = PATH_LNG_SPAN / (count + 1)
        val spots: MutableList<GeoPoint> = ArrayList()
        for (i in 1..count) {
            spots.add(GeoPoint(37.5, 127.0 + i * step))
        }
        return spots
    }

    /** 기준을 낮추면 절반만 깔아도 포화돼 두 점수가 같아진다 — 그때 이 단언이 깨진다. */
    @Test
    fun `종류마다 포화 기준 아래는 위보다 낮다`() {
        for (type in WEIGHTED_TYPES) {
            val full: List<GeoPoint> = saturatingSpots(type)
            val half: List<GeoPoint> = full.take(full.size / 2)

            val halfScore: Int = scorer(FakeSafetySpotSource.of(type, half), NIGHT).score(path).score
            val fullScore: Int = scorer(FakeSafetySpotSource.of(type, full), NIGHT).score(path).score

            assertThat(halfScore).describedAs("%s 포화 아래", type).isLessThan(fullScore)
        }
    }

    /** 그 종류만 포화 기준을 넘기도록 깔고 점수를 잰다. */
    private fun scoreWithSaturated(type: SafetySpotType, clock: Clock): Int =
        scorer(FakeSafetySpotSource.of(type, saturatingSpots(type)), clock).score(path).score

    private fun saturatingSpots(type: SafetySpotType): List<GeoPoint> {
        val needed: Int = when (type) {
            SafetySpotType.CCTV -> 10
            SafetySpotType.STREET_LAMP -> 16
            SafetySpotType.SECURITY_LAMP -> 16
            SafetySpotType.BELL -> 6
            SafetySpotType.POLICE -> 2
            SafetySpotType.SAFEHOUSE -> 3
            else -> 0
        }
        val spots: MutableList<GeoPoint> = ArrayList()
        for (i in 1..needed) {
            spots.add(GeoPoint(37.5, 127.0 + i * 0.0002))
        }
        return spots
    }

    @Test
    fun `데이터가 비면 점수 0으로 수렴`() {
        val result = scorer(FakeSafetySpotSource(), NIGHT).score(path)
        assertThat(result.score).isZero()
        assertThat(result.breakdown.cctvDensity).isZero()
        assertThat(result.breakdown.routeKm).isGreaterThan(0.0)
    }

    @Test
    fun `회랑 안 CCTV 가 점수와 밀도를 올린다`() {
        val cctv = (1..9).map { GeoPoint(37.5, 127.0 + it * 0.001) }
        val result = scorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, cctv), NIGHT).score(path)
        assertThat(result.score).isGreaterThan(0)
        assertThat(result.score).isLessThanOrEqualTo(100)
        assertThat(result.breakdown.cctvDensity).isGreaterThan(9.0)
    }

    @Test
    fun `회랑 안 지구대·파출소가 점수와 개수에 기여한다`() {
        val police = listOf(GeoPoint(37.5, 127.005))
        val result = scorer(FakeSafetySpotSource.of(SafetySpotType.POLICE, police), NIGHT).score(path)
        assertThat(result.breakdown.policeCount).isEqualTo(1)
        assertThat(result.score).isGreaterThan(0)
    }

    @Test
    fun `회랑 안 가로등이 점수와 밀도에 기여한다`() {
        val lamps = (1..12).map { GeoPoint(37.5, 127.0 + it * 0.0007) }
        val result = scorer(FakeSafetySpotSource.of(SafetySpotType.STREET_LAMP, lamps), NIGHT).score(path)
        assertThat(result.breakdown.lampDensity).isGreaterThan(0.0)
        assertThat(result.score).isGreaterThan(0)
    }

    /**
     * 감점 쪽은 실데이터가 아직 없어(안전Dream 고유아이디 대기) 테스트가 유일한 증거다.
     * 경로는 약 0.88km 라 우범지역 2곳이면 km 당 2.3 으로 포화(SAT_RISK_PER_KM=2.0)에 이른다.
     */
    @Test
    fun `우범지역은 점수를 깎는다 - 포화 시 감점 상한만큼`() {
        val cctv = (1..40).map { GeoPoint(37.5, 127.0 + it * 0.00025) }
        val withoutRisk: Int = scorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, cctv), NIGHT).score(path).score

        val risky = FakeSafetySpotSource(
            mapOf(
                SafetySpotType.CCTV to cctv.map { SafetySpotPoint(SafetySpotType.CCTV, it.lat, it.lng) },
                SafetySpotType.CRIME_PRONE_AREA to listOf(
                    SafetySpotPoint(SafetySpotType.CRIME_PRONE_AREA, 37.5, 127.002),
                    SafetySpotPoint(SafetySpotType.CRIME_PRONE_AREA, 37.5, 127.008),
                ),
            ),
        )
        val result = scorer(risky, NIGHT).score(path)

        assertThat(result.breakdown.crimeProneAreaCount).isEqualTo(2)
        assertThat(result.score).isEqualTo(withoutRisk - 20)
    }

    @Test
    fun `우범지역이 없으면 점수를 건드리지 않는다`() {
        val cctv = (1..40).map { GeoPoint(37.5, 127.0 + it * 0.00025) }
        val result = scorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, cctv), NIGHT).score(path)

        assertThat(result.breakdown.crimeProneAreaCount).isZero()
    }

    @Test
    fun `우범지역만 있으면 점수는 0 아래로 내려가지 않는다`() {
        val risky = FakeSafetySpotSource(
            mapOf(
                SafetySpotType.CRIME_PRONE_AREA to listOf(
                    SafetySpotPoint(SafetySpotType.CRIME_PRONE_AREA, 37.5, 127.002),
                    SafetySpotPoint(SafetySpotType.CRIME_PRONE_AREA, 37.5, 127.008),
                ),
            ),
        )

        assertThat(scorer(risky, NIGHT).score(path).score).isZero()
    }

    /** 시설은 30m 회랑이지만 우범지역은 구역이라 100m 로 본다 — 50m 밖 시설은 안 세고 우범지역은 센다. */
    @Test
    fun `우범지역은 시설보다 넓은 회랑으로 센다`() {
        val offCorridor = GeoPoint(37.5005, 127.005)
        val risky = FakeSafetySpotSource(
            mapOf(
                SafetySpotType.CCTV to listOf(SafetySpotPoint(SafetySpotType.CCTV, offCorridor.lat, offCorridor.lng)),
                SafetySpotType.CRIME_PRONE_AREA to listOf(
                    SafetySpotPoint(SafetySpotType.CRIME_PRONE_AREA, offCorridor.lat, offCorridor.lng),
                ),
            ),
        )
        val result = scorer(risky, NIGHT).score(path)

        assertThat(result.breakdown.cctvDensity).isZero()
        assertThat(result.breakdown.crimeProneAreaCount).isEqualTo(1)
    }

    @Test
    fun `회랑 밖 시설은 점수에 반영되지 않는다`() {
        val offCorridor = listOf(GeoPoint(37.502, 127.005))
        val scored = scorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, offCorridor), NIGHT)
        assertThat(scored.score(path).breakdown.cctvDensity).isZero()
    }

    /**
     * 안심귀갓길은 전국 원천이 2건뿐이라 실측 후보 16개에서 겹침이 전부 0 이었다 —
     * 0.15 를 들고 있던 자리를 0.03 으로 줄이고 나머지를 데이터가 있는 축에 넘겼다.
     * 항목 자체는 남겨 두어, 데이터가 채워지면 그대로 되살아난다.
     */
    @Test
    fun `안심귀갓길 겹침은 회수 후 남긴 safePath 가중치(3점)만큼 기여`() {
        val source = FakeSafetySpotSource(safePaths = listOf(SafePath(path)))
        val result = scorer(source, NIGHT).score(path)
        assertThat(result.breakdown.safePathOverlap).isGreaterThan(0.9)
        assertThat(result.score).isEqualTo(3)
    }

    @Test
    fun `결정론 - 같은 경로와 데이터는 같은 점수`() {
        val source = FakeSafetySpotSource.of(SafetySpotType.BELL, listOf(GeoPoint(37.5, 127.005)))
        val scored = scorer(source, NIGHT)
        assertThat(scored.score(path)).isEqualTo(scored.score(path))
    }

    @Test
    fun `0 길이 경로도 폭주 없이 안전하게 0`() {
        val degenerate = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))
        val source = FakeSafetySpotSource.of(SafetySpotType.CCTV, listOf(GeoPoint(37.5, 127.0)))
        val result = scorer(source, NIGHT).score(degenerate)
        assertThat(result.score).isBetween(0, 100)
    }

    private fun scorer(source: FakeSafetySpotSource, clock: Clock): SafetyScorer =
        SafetyScorer(source, SafetyWeightsCatalog(), clock)

    private companion object {

        /** 8월 15일 서울 일몰은 19:24 — 22시는 밤, 12시는 낮이다. */
        val NIGHT: Clock = Clock.fixed(Instant.parse("2026-08-15T13:00:00Z"), ZoneOffset.UTC)
        val DAY: Clock = Clock.fixed(Instant.parse("2026-08-15T03:00:00Z"), ZoneOffset.UTC)

        /** 테스트 경로 길이(km). 밀도 → 개수 환산에 쓴다. */
        const val ROUTE_KM: Double = 0.882

        /** 어떤 상한을 넣어도 포화하는 밀도 — 축의 만점을 재는 분모로 쓴다. */
        const val ALWAYS_SATURATING_PER_KM: Double = 200.0

        /** 테스트 경로가 덮는 경도 폭. */
        const val PATH_LNG_SPAN: Double = 0.01

        /** 서울 4개 구간 16개 후보 실측(2026-08-23). */
        const val MEASURED_CCTV_MEDIAN_PER_KM: Double = 2.82
        const val MEASURED_LAMP_TYPICAL_PER_KM: Double = 14.8

        val WEIGHTED_TYPES: List<SafetySpotType> = listOf(
            SafetySpotType.CCTV, SafetySpotType.STREET_LAMP, SafetySpotType.SECURITY_LAMP,
            SafetySpotType.BELL, SafetySpotType.POLICE, SafetySpotType.SAFEHOUSE,
        )
    }
}
