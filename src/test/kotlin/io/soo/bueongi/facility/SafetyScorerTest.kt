package io.soo.bueongi.facility

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [SafetyScorer] 단위 테스트(프론트 safetyScore.ts 동형). 회랑 밀도/겹침 → 0~100 점 매핑과
 * 데이터 부재 시 0 수렴, 가중치(safePath 0.15) 기여를 고정한다.
 */
class SafetyScorerTest {

    // 약 888m 동서 직선 경로(lat 37.5, lng 127.0→127.01).
    private val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))

    @Test
    fun `데이터가 비면 점수 0으로 수렴`() {
        val scorer = SafetyScorer(FakeFacilitySource())
        val result = scorer.score(path)
        assertThat(result.score).isZero()
        assertThat(result.breakdown.cctvDensity).isZero()
        assertThat(result.breakdown.routeKm).isGreaterThan(0.0)
    }

    @Test
    fun `회랑 안 CCTV 가 점수와 밀도를 올린다`() {
        val cctv = (1..9).map { GeoPoint(37.5, 127.0 + it * 0.001) } // 경로 위 9개
        val scorer = SafetyScorer(FakeFacilitySource.of(FacilityType.CCTV, cctv))
        val result = scorer.score(path)
        assertThat(result.score).isGreaterThan(0)
        assertThat(result.score).isLessThanOrEqualTo(100)
        // 9개 / 약 0.888km ≈ 10/km.
        assertThat(result.breakdown.cctvDensity).isGreaterThan(9.0)
    }

    @Test
    fun `회랑 밖 시설은 점수에 반영되지 않는다`() {
        val offCorridor = listOf(GeoPoint(37.502, 127.005)) // 경로에서 ~약 222m
        val scorer = SafetyScorer(FakeFacilitySource.of(FacilityType.CCTV, offCorridor))
        assertThat(scorer.score(path).breakdown.cctvDensity).isZero()
    }

    @Test
    fun `안심귀갓길 겹침은 safePath 가중치(약 15점)만큼 기여`() {
        // 경로와 동일한 좌표열의 안심귀갓길 → overlap 비율 ≈ 1.0 → 0.15 * 100 = 15점.
        val source = FakeFacilitySource(safePaths = listOf(SafePath(path)))
        val result = SafetyScorer(source).score(path)
        assertThat(result.breakdown.safePathOverlap).isGreaterThan(0.9)
        assertThat(result.score).isEqualTo(15)
    }

    @Test
    fun `결정론 - 같은 경로와 데이터는 같은 점수`() {
        val source = FakeFacilitySource.of(FacilityType.BELL, listOf(GeoPoint(37.5, 127.005)))
        val scorer = SafetyScorer(source)
        assertThat(scorer.score(path)).isEqualTo(scorer.score(path))
    }

    @Test
    fun `0 길이 경로도 폭주 없이 안전하게 0`() {
        val degenerate = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))
        val source = FakeFacilitySource.of(FacilityType.CCTV, listOf(GeoPoint(37.5, 127.0)))
        val result = SafetyScorer(source).score(degenerate)
        assertThat(result.score).isBetween(0, 100)
    }
}
