package io.soo.bueongi.core.route.safety

import io.soo.bueongi.core.route.safety.service.SafetyScorer
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafetyScorerTest {

    private val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))

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
        val scorer: SafetyScorer = SafetyScorer(source)
        assertThat(scorer.score(path)).isEqualTo(scorer.score(path))
    }

    @Test
    fun `0 길이 경로도 폭주 없이 안전하게 0`() {
        val degenerate = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))
        val source = FakeSafetySpotSource.of(SafetySpotType.CCTV, listOf(GeoPoint(37.5, 127.0)))
        val result = SafetyScorer(source).score(degenerate)
        assertThat(result.score).isBetween(0, 100)
    }
}
