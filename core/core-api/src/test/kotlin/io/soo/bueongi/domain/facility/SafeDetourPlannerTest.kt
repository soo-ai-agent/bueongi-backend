package io.soo.bueongi.domain.facility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [SafeDetourPlanner] 단위 테스트. 직접경로 주변 시설 클러스터 → 앵커, 진행방향 중간 구간의 고가중 앵커 →
 * 경유지 조합 생성, 끝점 근처/과도 측방이탈 제외와 출발거리순 정렬을 고정한다.
 */
class SafeDetourPlannerTest {

    // 약 888m 동서 직선(origin 127.0 → dest 127.01).
    private val origin = GeoPoint(37.5, 127.0)
    private val dest = GeoPoint(37.5, 127.01)
    private val directPath = listOf(origin, dest)

    @Test
    fun `extractSafeAnchors - 밀집 클러스터를 weight 로 묶고 내림차순 상위 반환`() {
        // 경로 중간(127.005)에 CCTV 3개 밀집(같은 ~80m 셀), 다른 지점(127.007)에 비상벨 1개.
        val cctvCluster = listOf(
            GeoPoint(37.50002, 127.00500),
            GeoPoint(37.50003, 127.00501),
            GeoPoint(37.50001, 127.00502),
        )
        val bell = listOf(GeoPoint(37.5, 127.007))
        val source = FakeFacilitySource(
            mapOf(
                FacilityType.CCTV to cctvCluster.map { FacilityPoint(FacilityType.CCTV, it.lat, it.lng) },
                FacilityType.BELL to bell.map { FacilityPoint(FacilityType.BELL, it.lat, it.lng) },
            ),
        )
        val anchors = SafeDetourPlanner(source).extractSafeAnchors(directPath)

        assertThat(anchors).isNotEmpty
        // 최고 weight 앵커는 CCTV 3개 클러스터.
        assertThat(anchors.first().weight).isEqualTo(3)
        // weight 내림차순.
        assertThat(anchors.map { it.weight }).isSortedAccordingTo(Comparator.reverseOrder())
        // 클러스터 중심은 127.005 근처.
        assertThat(anchors.first().center.lng).isCloseTo(127.005, org.assertj.core.api.Assertions.within(0.001))
    }

    @Test
    fun `extractSafeAnchors - 회랑 밖 시설은 앵커가 되지 않는다`() {
        val far = listOf(GeoPoint(37.51, 127.005)) // 경로에서 1km 이상
        val source = FakeFacilitySource.of(FacilityType.CCTV, far)
        assertThat(SafeDetourPlanner(source).extractSafeAnchors(directPath)).isEmpty()
    }

    @Test
    fun `pickDetourWaypoints - 중간 구간 두 앵커로 단일과 쌍 조합 생성(출발거리순)`() {
        val planner = SafeDetourPlanner(FakeFacilitySource())
        val a = SafeAnchor(GeoPoint(37.5004, 127.004), weight = 5) // t≈0.4
        val d = SafeAnchor(GeoPoint(37.5004, 127.006), weight = 3) // t≈0.6
        val combos = planner.pickDetourWaypoints(origin, dest, listOf(a, d))

        // 단일 2개 + 쌍 1개.
        assertThat(combos).hasSize(3)
        assertThat(combos).contains(listOf(a.center), listOf(d.center))
        // 쌍 조합은 출발거리순(t 오름차순: a 먼저).
        assertThat(combos).contains(listOf(a.center, d.center))
    }

    @Test
    fun `pickDetourWaypoints - 끝점 근처와 과도 측방이탈 앵커 제외`() {
        val planner = SafeDetourPlanner(FakeFacilitySource())
        val nearOrigin = SafeAnchor(GeoPoint(37.5, 127.0005), weight = 9) // t≈0.05 → 제외
        val farLateral = SafeAnchor(GeoPoint(37.503, 127.005), weight = 9) // 측방 ~333m > 222m → 제외
        val combos = planner.pickDetourWaypoints(origin, dest, listOf(nearOrigin, farLateral))
        assertThat(combos).isEmpty()
    }

    @Test
    fun `pickDetourWaypoints - 앵커 없으면 빈 결과`() {
        val planner = SafeDetourPlanner(FakeFacilitySource())
        assertThat(planner.pickDetourWaypoints(origin, dest, emptyList())).isEmpty()
    }
}
