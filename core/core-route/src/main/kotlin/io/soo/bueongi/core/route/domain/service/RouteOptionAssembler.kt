package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.api.response.DataProvenance
import io.soo.bueongi.core.route.api.response.RouteOption
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.model.RouteTagVariant
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.support.util.formatDistance
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.roundToInt

@Component
class RouteOptionAssembler(
    private val markerBuilder: RouteMarkerBuilder,
    private val safetySpotSource: SafetySpotSpatialSource,
    private val routeTypeCatalog: RouteTypeCatalog,
) {
    internal fun assemble(scoredRoutes: List<ScoredRoute>, origin: GeoPoint, destination: GeoPoint): List<RouteOption> {
        val provenance: DataProvenance = buildProvenance()
        val ordered: List<ScoredRoute> = scoredRoutes.sortedByDescending { it.score.rank }
        val options: ArrayList<RouteOption> = ArrayList(ordered.size)
        for ((index, scoredRoute) in ordered.withIndex()) {
            val candidate: RouteCandidate = scoredRoute.candidate
            val breakdown: SafetyBreakdown = scoredRoute.score.breakdown
            val minutes: Int = max(1, (candidate.timeS / 60.0).roundToInt())
            val markers: List<SafetySpotPoi> = markerBuilder.buildMarkers(origin, destination, candidate.path)
            val option: RouteOption =
                RouteOption(
                    id = "tmap-${candidate.searchOption}-$index",
                    name = routeTypeCatalog.displayOf(scoredRoute.type).scoredName,
                    time = "${minutes}분",
                    dist = formatDistance(candidate.distanceM),
                    desc = describe(scoredRoute.score.score, breakdown),
                    tags = buildTags(scoredRoute),
                    type = scoredRoute.type,
                    path = candidate.path,
                    steps = candidate.steps,
                    score = scoredRoute.score.score,
                    markers = markers,
                    breakdown = breakdown,
                    provenance = provenance,
                )
            options.add(option)
        }
        return options
    }

    private fun describe(score: Int?, breakdown: SafetyBreakdown): String {
        if (score == null) {
            return "이 지역 안심시설 자료가 없어 안전 점수를 낼 수 없습니다"
        }
        return "안전 점수 ${score}점 · CCTV ${breakdown.cctvDensity}/km"
    }

    private fun buildTags(scoredRoute: ScoredRoute): List<RouteTag> {
        val tags: ArrayList<RouteTag> = ArrayList(routeTypeCatalog.displayOf(scoredRoute.type).signatureTags)
        val breakdown: SafetyBreakdown = scoredRoute.score.breakdown
        if (breakdown.cctvDensity >= TAG_CCTV_DENSITY_MIN_PER_KM) {
            tags.add(RouteTag("CCTV 많음", RouteTagVariant.MINT))
        }
        if (breakdown.lampDensity >= TAG_LAMP_DENSITY_MIN_PER_KM) {
            tags.add(RouteTag("밝은 길", RouteTagVariant.YELLOW))
        }
        if (breakdown.safePathOverlap >= TAG_SAFE_PATH_OVERLAP_MIN) {
            tags.add(RouteTag("안심귀갓길", RouteTagVariant.MINT))
        }
        if (breakdown.safehouseCount >= 1) {
            tags.add(RouteTag("안심지킴이집", RouteTagVariant.OUTLINE))
        }
        if (breakdown.policeCount >= 1) {
            tags.add(RouteTag("지구대 인접", RouteTagVariant.BLUE))
        }
        return tags
    }

    private fun buildProvenance(): DataProvenance {
        val generatedAt: String? = safetySpotSource.freshness?.generatedAt
        var basedOn: String = ""
        if (generatedAt != null && generatedAt.length >= YEAR_MONTH_LENGTH) {
            basedOn = generatedAt.substring(0, YEAR_MONTH_LENGTH).replace('-', '.')
        }
        return DataProvenance(kind = "live", basedOn = basedOn, origin = "공공데이터포털·서울 열린데이터광장")
    }

    private companion object {
        const val YEAR_MONTH_LENGTH: Int = 7
        const val TAG_CCTV_DENSITY_MIN_PER_KM: Double = 5.0
        const val TAG_LAMP_DENSITY_MIN_PER_KM: Double = 10.0
        const val TAG_SAFE_PATH_OVERLAP_MIN: Double = 0.2
    }
}
