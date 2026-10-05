package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.model.DataProvenance
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteOption
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
        return ordered
            .mapIndexed { index, scoredRoute ->
                val candidate: RouteCandidate = scoredRoute.candidate
                val breakdown: SafetyBreakdown = scoredRoute.score.breakdown
                RouteOption(
                    id = "tmap-${candidate.searchOption}-$index",
                    name = routeTypeCatalog.displayOf(scoredRoute.type).scoredName,
                    time = "${max(1, (candidate.timeS / 60.0).roundToInt())}분",
                    dist = formatDistance(candidate.distanceM),
                    desc = describe(scoredRoute.score.score, breakdown),
                    tags = buildTags(scoredRoute),
                    type = scoredRoute.type,
                    path = candidate.path,
                    steps = candidate.steps,
                    score = scoredRoute.score.score,
                    markers = markerBuilder.buildMarkers(origin, destination, candidate.path),
                    breakdown = breakdown,
                    provenance = provenance,
                )
            }
    }

    private fun describe(score: Int?, breakdown: SafetyBreakdown): String {
        if (score == null) {
            return NO_DATA_DESC
        }
        return "안전 점수 ${score}점 · CCTV ${breakdown.cctvDensity}/km"
    }

    private fun buildTags(scoredRoute: ScoredRoute): List<RouteTag> =
        buildList {
            addAll(routeTypeCatalog.displayOf(scoredRoute.type).signatureTags)
            val breakdown: SafetyBreakdown = scoredRoute.score.breakdown
            if (breakdown.cctvDensity >= TAG_CCTV_DENSITY_MIN_PER_KM) {
                add(RouteTag("CCTV 많음", RouteTagVariant.MINT))
            }
            if (breakdown.lampDensity >= TAG_LAMP_DENSITY_MIN_PER_KM) {
                add(RouteTag("밝은 길", RouteTagVariant.YELLOW))
            }
            if (breakdown.safePathOverlap >= TAG_SAFE_PATH_OVERLAP_MIN) {
                add(RouteTag("안심귀갓길", RouteTagVariant.MINT))
            }
            if (breakdown.safehouseCount >= 1) {
                add(RouteTag("안심지킴이집", RouteTagVariant.OUTLINE))
            }
            if (breakdown.policeCount >= 1) {
                add(RouteTag("지구대 인접", RouteTagVariant.BLUE))
            }
        }

    private fun buildProvenance(): DataProvenance {
        val generatedAt: String? = safetySpotSource.freshness?.generatedAt
        val basedOn: String =
            if (generatedAt != null && generatedAt.length >= YEAR_MONTH_LENGTH) {
                generatedAt.substring(0, YEAR_MONTH_LENGTH).replace('-', '.')
            } else {
                ""
            }
        return DataProvenance(kind = "live", basedOn = basedOn, origin = DATA_ORIGIN)
    }

    private companion object {
        const val DATA_ORIGIN: String = "공공데이터포털·서울 열린데이터광장"
        const val NO_DATA_DESC: String = "이 지역 안심시설 자료가 없어 안전 점수를 낼 수 없습니다"
        const val YEAR_MONTH_LENGTH: Int = 7
        const val TAG_CCTV_DENSITY_MIN_PER_KM: Double = 5.0
        const val TAG_LAMP_DENSITY_MIN_PER_KM: Double = 10.0
        const val TAG_SAFE_PATH_OVERLAP_MIN: Double = 0.2
    }
}
