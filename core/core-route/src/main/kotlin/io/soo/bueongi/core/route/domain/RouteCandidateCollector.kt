package io.soo.bueongi.core.route.domain

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.route.domain.safety.SafeAnchor
import io.soo.bueongi.core.route.domain.safety.SafeDetourPlanner
import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.storage.db.core.apilog.measure
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Component

/** Tmap 에서 경로 후보를 모은다 — 기본 후보 + 안심 거점 경유 후보, 중복 제거까지. */
@Component
class RouteCandidateCollector(
    private val tmapClient: TmapRouteClient,
    private val detourPlanner: SafeDetourPlanner,
    private val candidateMerger: RouteCandidateMerger,
    private val apiCallLogStore: ApiCallLogStore? = null,
) {

    val isEnabled: Boolean get() = tmapClient.isEnabled

    /** 기본 경로 후보(추천·대로우선·최단). 전멸하면 빈 목록 — 호출부가 폴백을 결정한다. */
    fun collectBase(query: RouteQuery): List<TmapRoute> =
        // ponytail: service 레벨 계측 — HTTP statusCode 등 client 내부 상세는 후속. client 모듈 계측 훅(nullable recorder 주입)으로 승격 가능.
        apiCallLogStore.measure(
            provider = ApiProvider.TMAP,
            endpoint = "route",
            success = { it.isNotEmpty() },
            detail = {
                if (it.isEmpty()) {
                    "경로 후보 0건 — 폴백"
                } else {
                    null
                }
            },
        ) {
            tmapClient.fetchRoutes(
                query.origin.toTmapCoord(),
                query.destination.toTmapCoord(),
                query.destinationName,
            )
        }

    /** 기본 후보에 안심 우회 후보를 더해 중복을 걷어낸 최종 후보 목록. */
    fun collectAll(query: RouteQuery, base: List<TmapRoute>): List<RouteCandidate> {
        val detours: List<TmapRoute> = collectSafeDetours(query, base)
        return candidateMerger.mergeNearDuplicates(
            dedupeByIdentity(base + detours).map { it.toCandidate() },
        )
    }

    /**
     * 안심 거점을 경유하는 우회 후보. 최단 경로 대비 우회 예산(안심 강도)을 넘는 후보는 버린다.
     * 너무 멀리 도는 길은 안전해도 사용자가 쓰지 않기 때문이다.
     */
    private fun collectSafeDetours(query: RouteQuery, base: List<TmapRoute>): List<TmapRoute> {
        val directPath: List<GeoPoint> = base.first().path.toGeoPoints()
        val anchors: List<SafeAnchor> = detourPlanner.extractSafeAnchors(directPath)
        val waypointCombos: List<List<GeoPoint>> =
            detourPlanner.pickDetourWaypoints(query.origin, query.destination, anchors)

        val maxDistanceM: Double = base.minOf { it.distanceM } * (1 + query.safetyPreference.detourBudget)
        val detours: ArrayList<TmapRoute> = ArrayList<TmapRoute>()
        for (waypoints in waypointCombos) {
            val route: TmapRoute = tmapClient.fetchRouteVia(
                query.origin.toTmapCoord(),
                query.destination.toTmapCoord(),
                waypoints.map { it.toTmapCoord() },
                destName = query.destinationName,
            ) ?: continue
            if (route.distanceM <= maxDistanceM) {
                detours.add(route)
            }
        }
        return detours
    }

    private fun dedupeByIdentity(routes: List<TmapRoute>): List<TmapRoute> {
        val seen: HashSet<Long> = HashSet<Long>()
        return routes.filter { seen.add(it.identityKey) }
    }
}

private fun TmapRoute.toCandidate(): RouteCandidate =
    RouteCandidate(searchOption = searchOption, path = path.toGeoPoints(), distanceM = distanceM, timeS = timeS, steps = steps)

private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
