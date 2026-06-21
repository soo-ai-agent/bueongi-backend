package io.soo.bueongi.routes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [RouteType.from] 매핑 규칙 테스트. 미상/누락은 안전 우선(SAFE) 기본으로 떨어지는 것이 도메인 계약.
 */
class RouteTypeTest {

    @Test
    fun `apiType 으로 매핑`() {
        assertThat(RouteType.from("safe")).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.from("main")).isEqualTo(RouteType.MAIN)
        assertThat(RouteType.from("fast")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `id 문자열로 매핑`() {
        assertThat(RouteType.from("1")).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.from("2")).isEqualTo(RouteType.MAIN)
        assertThat(RouteType.from("3")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `대소문자 무시 및 공백 트림`() {
        assertThat(RouteType.from("  SAFE ")).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.from("Fast")).isEqualTo(RouteType.FAST)
    }

    @Test
    fun `null 또는 미상 값은 안전 우선 SAFE 기본`() {
        assertThat(RouteType.from(null)).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.from("")).isEqualTo(RouteType.SAFE)
        assertThat(RouteType.from("unknown")).isEqualTo(RouteType.SAFE)
    }

    @Test
    fun `안전 경로일수록 우회 계수가 크고 최단일수록 작다`() {
        assertThat(RouteType.SAFE.distanceFactor)
            .isGreaterThan(RouteType.MAIN.distanceFactor)
        assertThat(RouteType.MAIN.distanceFactor)
            .isGreaterThan(RouteType.FAST.distanceFactor)
    }

    @Test
    fun `추천 경로일수록 안심시설(CCTV-비상벨-경찰) 밀도가 엄격히 높다`() {
        // 앱의 핵심 가치 약속(RouteType doc): safe→main→fast 로 갈수록 안심시설이 준다.
        // 즉 '추천 경로(SAFE)'는 per-km 안심시설(CCTV·비상벨·경찰) 밀도가 항상 가장 높아야 한다 — SAFE 의
        // 설명("가장 밝고 안심 시설이 잘 갖춰진 길")·태그("CCTV 많음")가 곧 이 단조성이다.
        // 기존 테스트는 distanceFactor 단조성만, FacilityServiceTest 는 total 기준 SAFE>FAST(MAIN 누락) 만 고정한다.
        // total 은 storePerKm(MAIN 최대)·distanceFactor 가 부풀리므로 cctv 밀도가 역전돼도 SAFE>FAST total 은 통과할 수 있어,
        // 안심시설 밀도 자체의 SAFE>MAIN>FAST 단조성은 상수 레벨에서 미고정이었다. 밀도 상수 튜닝 시 핵심 안전 순서가
        // 조용히 깨지는 것(예: FAST.cctvPerKm > SAFE.cctvPerKm)을 막기 위해 상수 원천에서 직접 봉인한다.
        assertThat(RouteType.SAFE.cctvPerKm).isGreaterThan(RouteType.MAIN.cctvPerKm)
        assertThat(RouteType.MAIN.cctvPerKm).isGreaterThan(RouteType.FAST.cctvPerKm)
        assertThat(RouteType.SAFE.bellPerKm).isGreaterThan(RouteType.MAIN.bellPerKm)
        assertThat(RouteType.MAIN.bellPerKm).isGreaterThan(RouteType.FAST.bellPerKm)
        assertThat(RouteType.SAFE.policePerKm).isGreaterThan(RouteType.MAIN.policePerKm)
        assertThat(RouteType.MAIN.policePerKm).isGreaterThan(RouteType.FAST.policePerKm)
    }

    @Test
    fun `편의점 밀도는 안심시설과 달리 주요 도로(MAIN)에서 가장 높다`() {
        // 대조군/의도 고정: store 는 '안심시설 밀도 단조성'에서 의도적으로 제외된다. 편의점은 상업시설이라
        // 유동인구가 많은 주요 도로(MAIN) 위주의 길에서 가장 많은 것이 도메인상 자연스럽다(MAIN 태그 "유동인구 많음").
        // 위 단조성 테스트를 store 까지 확장하려는 '실수'를 막는 가드 — store 비단조성은 버그가 아니라 설계다.
        assertThat(RouteType.MAIN.storePerKm).isGreaterThan(RouteType.SAFE.storePerKm)
        assertThat(RouteType.MAIN.storePerKm).isGreaterThan(RouteType.FAST.storePerKm)
    }
}
