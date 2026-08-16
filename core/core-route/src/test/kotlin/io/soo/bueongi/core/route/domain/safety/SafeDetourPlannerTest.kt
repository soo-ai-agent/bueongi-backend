package io.soo.bueongi.core.route.domain.safety

import io.soo.bueongi.core.route.domain.safety.model.SafeAnchor
import io.soo.bueongi.core.route.domain.safety.service.SafeDetourPlanner
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafeDetourPlannerTest {

    private val origin: GeoPoint = GeoPoint(37.5, 127.0)
    private val dest: GeoPoint = GeoPoint(37.5, 127.01)
    private val directPath = listOf(origin, dest)

    @Test
    fun `extractSafeAnchors - 밀집 클러스터를 weight 로 묶고 내림차순 상위 반환`() {
        val cctvCluster = listOf(
            GeoPoint(37.50002, 127.00500),
            GeoPoint(37.50003, 127.00501),
            GeoPoint(37.50001, 127.00502),
        )
        val bell = listOf(GeoPoint(37.5, 127.007))
        val source = FakeSafetySpotSource(
            mapOf(
                SafetySpotType.CCTV to cctvCluster.map { SafetySpotPoint(SafetySpotType.CCTV, it.lat, it.lng) },
                SafetySpotType.BELL to bell.map { SafetySpotPoint(SafetySpotType.BELL, it.lat, it.lng) },
            ),
        )
        val anchors = SafeDetourPlanner(source).extractSafeAnchors(directPath)

        assertThat(anchors).isNotEmpty
        assertThat(anchors.first().weight).isEqualTo(3)
        assertThat(anchors.map { it.weight }).isSortedAccordingTo(Comparator.reverseOrder())
        assertThat(anchors.first().center.lng).isCloseTo(127.005, org.assertj.core.api.Assertions.within(0.001))
    }

    @Test
    fun `extractSafeAnchors - 회랑 밖 시설은 앵커가 되지 않는다`() {
        val far = listOf(GeoPoint(37.51, 127.005))
        val source = FakeSafetySpotSource.of(SafetySpotType.CCTV, far)
        assertThat(SafeDetourPlanner(source).extractSafeAnchors(directPath)).isEmpty()
    }

    @Test
    fun `pickDetourWaypoints - 중간 구간 두 앵커로 단일과 쌍 조합 생성(출발거리순)`() {
        val planner = SafeDetourPlanner(FakeSafetySpotSource())
        val a = SafeAnchor(GeoPoint(37.5004, 127.004), weight = 5)
        val d = SafeAnchor(GeoPoint(37.5004, 127.006), weight = 3)
        val combos = planner.pickDetourWaypoints(origin, dest, listOf(a, d))

        assertThat(combos).hasSize(3)
        assertThat(combos).contains(listOf(a.center), listOf(d.center))
        assertThat(combos).contains(listOf(a.center, d.center))
    }

    @Test
    fun `pickDetourWaypoints - 끝점 근처와 과도 측방이탈 앵커 제외`() {
        val planner = SafeDetourPlanner(FakeSafetySpotSource())
        val nearOrigin = SafeAnchor(GeoPoint(37.5, 127.0005), weight = 9)
        val farLateral = SafeAnchor(GeoPoint(37.503, 127.005), weight = 9)
        val combos = planner.pickDetourWaypoints(origin, dest, listOf(nearOrigin, farLateral))
        assertThat(combos).isEmpty()
    }

    @Test
    fun `pickDetourWaypoints - 앵커 없으면 빈 결과`() {
        val planner = SafeDetourPlanner(FakeSafetySpotSource())
        assertThat(planner.pickDetourWaypoints(origin, dest, emptyList())).isEmpty()
    }
}
