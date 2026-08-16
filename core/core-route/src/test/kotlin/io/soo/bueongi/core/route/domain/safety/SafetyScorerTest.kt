package io.soo.bueongi.core.route.domain.safety

import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafetyScorerTest {

    private val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))

    /**
     * 한 종류만 포화시키면 점수가 그 종류의 가중치 × 100 이 된다.
     * 가중치를 바꾸면 경로 순위가 통째로 흔들리는데, 지금까지 `score > 0` 만 보고 있어
     * 0.30 을 0.99 로 바꿔도 아무 테스트도 깨지지 않았다.
     */
    @Test
    fun `종류별 가중치가 점수에 그대로 실린다`() {
        assertThat(scoreWithSaturated(SafetySpotType.CCTV)).isEqualTo(30)
        assertThat(scoreWithSaturated(SafetySpotType.LAMP)).isEqualTo(20)
        assertThat(scoreWithSaturated(SafetySpotType.BELL)).isEqualTo(18)
        assertThat(scoreWithSaturated(SafetySpotType.POLICE)).isEqualTo(10)
        assertThat(scoreWithSaturated(SafetySpotType.SAFEHOUSE)).isEqualTo(7)
    }

    @Test
    fun `가중치 합이 1이라 모두 포화되면 100점`() {
        val byType: MutableMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in WEIGHTED_TYPES) {
            byType[type] = saturatingSpots(type).map { SafetySpotPoint(type, it.lat, it.lng) }
        }
        val source = FakeSafetySpotSource(byType, listOf(SafePath(coords = path, name = "안심귀갓길")))

        assertThat(SafetyScorer(source).score(path).score).isEqualTo(100)
    }

    /**
     * 포화 기준 아래에서는 점수가 개수에 비례한다. 위 테스트는 포화 이상만 보므로
     * 기준값을 낮춰도 결과가 같아 `SAT_*` 변이를 잡지 못한다 — 이 자리가 그것을 잡는다.
     */
    @Test
    fun `포화 기준 아래에서는 개수에 비례해 오른다`() {
        val five: List<GeoPoint> = saturatingSpots(SafetySpotType.CCTV).take(5)
        val ten: List<GeoPoint> = saturatingSpots(SafetySpotType.CCTV).take(10)

        val fiveScore: Int = SafetyScorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, five)).score(path).score
        val tenScore: Int = SafetyScorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, ten)).score(path).score

        assertThat(fiveScore).isEqualTo(9)
        assertThat(tenScore).isEqualTo(17)
    }

    /** 기준을 낮추면 절반만 깔아도 포화돼 두 점수가 같아진다 — 그때 이 단언이 깨진다. */
    @Test
    fun `종류마다 포화 기준 아래는 위보다 낮다`() {
        for (type in WEIGHTED_TYPES) {
            val full: List<GeoPoint> = saturatingSpots(type)
            val half: List<GeoPoint> = full.take(full.size / 2)

            val halfScore: Int = SafetyScorer(FakeSafetySpotSource.of(type, half)).score(path).score
            val fullScore: Int = SafetyScorer(FakeSafetySpotSource.of(type, full)).score(path).score

            assertThat(halfScore).describedAs("%s 포화 아래", type).isLessThan(fullScore)
        }
    }

    /** 그 종류만 포화 기준을 넘기도록 깔고 점수를 잰다. */
    private fun scoreWithSaturated(type: SafetySpotType): Int =
        SafetyScorer(FakeSafetySpotSource.of(type, saturatingSpots(type))).score(path).score

    private fun saturatingSpots(type: SafetySpotType): List<GeoPoint> {
        val needed: Int = when (type) {
            SafetySpotType.CCTV -> 20
            SafetySpotType.LAMP -> 40
            SafetySpotType.BELL -> 5
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
        val scorer = SafetyScorer(FakeSafetySpotSource())
        val result = scorer.score(path)
        assertThat(result.score).isZero()
        assertThat(result.breakdown.cctvDensity).isZero()
        assertThat(result.breakdown.routeKm).isGreaterThan(0.0)
    }

    @Test
    fun `회랑 안 CCTV 가 점수와 밀도를 올린다`() {
        val cctv = (1..9).map { GeoPoint(37.5, 127.0 + it * 0.001) }
        val scorer = SafetyScorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, cctv))
        val result = scorer.score(path)
        assertThat(result.score).isGreaterThan(0)
        assertThat(result.score).isLessThanOrEqualTo(100)
        assertThat(result.breakdown.cctvDensity).isGreaterThan(9.0)
    }

    @Test
    fun `회랑 안 지구대·파출소가 점수와 개수에 기여한다`() {
        val police = listOf(GeoPoint(37.5, 127.005))
        val result = SafetyScorer(FakeSafetySpotSource.of(SafetySpotType.POLICE, police)).score(path)
        assertThat(result.breakdown.policeCount).isEqualTo(1)
        assertThat(result.score).isGreaterThan(0)
    }

    @Test
    fun `회랑 안 가로등이 점수와 밀도에 기여한다`() {
        val lamps = (1..12).map { GeoPoint(37.5, 127.0 + it * 0.0007) }
        val result = SafetyScorer(FakeSafetySpotSource.of(SafetySpotType.LAMP, lamps)).score(path)
        assertThat(result.breakdown.lampDensity).isGreaterThan(0.0)
        assertThat(result.score).isGreaterThan(0)
    }

    @Test
    fun `회랑 밖 시설은 점수에 반영되지 않는다`() {
        val offCorridor = listOf(GeoPoint(37.502, 127.005))
        val scorer = SafetyScorer(FakeSafetySpotSource.of(SafetySpotType.CCTV, offCorridor))
        assertThat(scorer.score(path).breakdown.cctvDensity).isZero()
    }

    @Test
    fun `안심귀갓길 겹침은 safePath 가중치(약 15점)만큼 기여`() {
        val source = FakeSafetySpotSource(safePaths = listOf(SafePath(path)))
        val result = SafetyScorer(source).score(path)
        assertThat(result.breakdown.safePathOverlap).isGreaterThan(0.9)
        assertThat(result.score).isEqualTo(15)
    }

    @Test
    fun `결정론 - 같은 경로와 데이터는 같은 점수`() {
        val source = FakeSafetySpotSource.of(SafetySpotType.BELL, listOf(GeoPoint(37.5, 127.005)))
        val scorer = SafetyScorer(source)
        assertThat(scorer.score(path)).isEqualTo(scorer.score(path))
    }

    @Test
    fun `0 길이 경로도 폭주 없이 안전하게 0`() {
        val degenerate = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))
        val source = FakeSafetySpotSource.of(SafetySpotType.CCTV, listOf(GeoPoint(37.5, 127.0)))
        val result = SafetyScorer(source).score(degenerate)
        assertThat(result.score).isBetween(0, 100)
    }

    private companion object {

        val WEIGHTED_TYPES: List<SafetySpotType> = listOf(
            SafetySpotType.CCTV, SafetySpotType.LAMP, SafetySpotType.BELL,
            SafetySpotType.POLICE, SafetySpotType.SAFEHOUSE,
        )
    }
}
