package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.route.safety.model.SafeAnchor
import io.soo.bueongi.core.route.safety.service.SafeDetourPlanner
import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.storage.db.core.apilog.measure
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint
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
        // 1. 안심 거점을 경유하는 후보를 추가로 받는다(기본 후보만으로는 선택지가 3개뿐이다).
        val detours: List<TmapRoute> = collectSafeDetours(query, base)

        // 2. 완전히 같은 경로를 걷어내고(dedupe), 3. 거의 겹치는 경로를 하나로 합친다(merge).
        //    Tmap 은 옵션만 다르면 사실상 같은 길을 여러 번 주므로 두 단계가 다 필요하다.
        return candidateMerger.mergeNearDuplicates(
            dedupeByIdentity(base + detours).map { it.toCandidate() },
        )
    }

    /**
     * 안심 거점을 경유하는 우회 후보. 최단 경로 대비 우회 예산(안심 강도)을 넘는 후보는 버린다.
     * 너무 멀리 도는 길은 안전해도 사용자가 쓰지 않기 때문이다.
     */
    private fun collectSafeDetours(query: RouteQuery, base: List<TmapRoute>): List<TmapRoute> {
        // 첫 후보를 기준선으로 삼아 그 주변 안심시설을 거점으로 뭉치고, 경유지 조합을 뽑는다.
        val directPath: List<GeoPoint> = base.first().path.toGeoPoints()
        val anchors: List<SafeAnchor> = detourPlanner.extractSafeAnchors(directPath)
        val waypointCombos: List<List<GeoPoint>> =
            detourPlanner.pickDetourWaypoints(query.origin, query.destination, anchors)

        // 우회 예산 = 가장 짧은 후보 × (1 + 안심 강도). 사용자가 고른 강도만큼만 돌아간다.
        val maxDistanceM: Double = base.minOf { it.distanceM } * (1 + detourBudgetOf(query.safetyPreference))
        val detours: ArrayList<TmapRoute> = ArrayList<TmapRoute>()
        // 조합 하나가 Tmap 호출 한 번이다. 받아 보고 예산을 넘으면 버린다.
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

    /** 안심 강도별 우회 허용 비율. 높을수록 더 멀리 돌아 안심 거점을 경유한다. */
    private fun detourBudgetOf(preference: SafetyPreference): Double = DETOUR_BUDGETS.getValue(preference)

    private fun dedupeByIdentity(routes: List<TmapRoute>): List<TmapRoute> {
        val seen: HashSet<Long> = HashSet<Long>()
        return routes.filter { seen.add(it.identityKey) }
    }

    private companion object {

        val DETOUR_BUDGETS: Map<SafetyPreference, Double> = mapOf(
            SafetyPreference.BALANCED to 0.2,
            SafetyPreference.SAFE to 0.35,
            SafetyPreference.SAFEST to 0.6,
        )
    }
}

private fun TmapRoute.toCandidate(): RouteCandidate =
    RouteCandidate(searchOption = searchOption, path = path.toGeoPoints(), distanceM = distanceM, timeS = timeS, steps = steps)

private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
