package io.soo.bueongi.core.route

import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.domain.service.RouteCandidateCollector
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import io.soo.bueongi.core.route.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.safety.service.SafeDetourPlanner
import io.soo.bueongi.core.route.safety.service.SafetyScorer
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.facility.store.FakeFacilitySource
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tmap 이 죽으면 사용자는 200 과 "추천 경로"를 받지만 그건 **실제 길이 아니라 직선거리 추정치**다.
 * 호출 계측 대시보드를 걷어낸 뒤로 이 알림이 그 사실을 아는 **유일한 창구**라, 여기서 고정한다.
 */
class FallbackAlertTest {

    private class RecordingNotifier : AlertNotifier {
        override val isEnabled: Boolean = true
        val keys: MutableList<String> = ArrayList()
        override fun notify(key: String, title: String, detail: String) {
            keys.add(key)
        }
    }

    private val source = FakeFacilitySource()

    private val query = RouteQuery(
        origin = GeoPoint(37.5, 127.0),
        destination = GeoPoint(37.5, 127.01),
        destinationName = "도착",
        routeType = RouteType.SAFE,
        safetyPreference = SafetyPreference.SAFE,
    )

    private fun service(tmap: TmapRouteClient, notifier: AlertNotifier): SafeRouteService =
        SafeRouteService(
            routeService = RouteService(RouteTypeCatalog()),
            collector = RouteCandidateCollector(tmap, SafeDetourPlanner(source), RouteCandidateMerger()),
            scorer = SafetyScorer(source),
            assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
            alertNotifier = notifier,
        )

    @Test
    fun `Tmap 후보가 전멸하면 폴백으로 응답하면서 알림을 쏜다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(emptyList())
        val notifier = RecordingNotifier()

        val result = service(tmap, notifier).safeCompare(query)

        assertThat(result).isNotEmpty()
        assertThat(result.first().score).isNull()
        assertThat(notifier.keys).containsExactly("tmap-route-fallback")
    }

    @Test
    fun `Tmap 키가 없으면 폴백이지만 장애가 아니라 알리지 않는다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(false)
        val notifier = RecordingNotifier()

        service(tmap, notifier).safeCompare(query)

        assertThat(notifier.keys).isEmpty()
    }
}
