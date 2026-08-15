package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.DataProvenance
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.safety.model.SafetyBreakdown
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.model.RouteTagVariant
import io.soo.bueongi.storage.db.core.facility.store.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint
import io.soo.bueongi.support.util.DistanceFormat
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.roundToInt

/** 채점 끝난 경로를 응답용 [RouteOption] 으로 만든다 — 표기·태그·마커·데이터 출처. */
@Component
class RouteOptionAssembler(
    private val markerBuilder: RouteMarkerBuilder,
    private val facilitySource: FacilitySpatialSource,
    private val routeTypeCatalog: RouteTypeCatalog,
) {

    internal fun assemble(scoredRoutes: List<ScoredRoute>, origin: GeoPoint, destination: GeoPoint): List<RouteOption> {
        // 출처 표기는 모든 경로가 같은 값을 쓰므로 한 번만 만든다.
        val provenance: DataProvenance = buildProvenance()
        // 안전 점수 높은 순으로 내려보낸다 — 프론트는 받은 순서대로 그린다.
        return scoredRoutes
            .sortedByDescending { it.score.score }
            .mapIndexed { index, scoredRoute ->
                val candidate: RouteCandidate = scoredRoute.candidate
                val breakdown: SafetyBreakdown = scoredRoute.score.breakdown
                RouteOption(
                    id = "tmap-${candidate.searchOption}-$index",
                    name = routeTypeCatalog.displayOf(scoredRoute.type).scoredName,
                    time = "${max(1, (candidate.timeS / 60.0).roundToInt())}분",
                    dist = DistanceFormat.formatDistance(candidate.distanceM),
                    desc = "안전 점수 ${scoredRoute.score.score}점 · CCTV ${breakdown.cctvDensity}/km",
                    tags = buildTags(scoredRoute),
                    type = scoredRoute.type,
                    path = candidate.path,
                    steps = candidate.steps,
                    score = scoredRoute.score.score,
                    // 마커는 경로마다 다시 계산한다(경로가 다르면 회랑도 다르다).
                    markers = markerBuilder.buildMarkers(origin, destination, candidate.path),
                    breakdown = breakdown,
                    provenance = provenance,
                )
            }
    }

    /** 유형 고유 태그(안심·빠른길) 뒤에, 채점 결과가 기준을 넘긴 항목만 덧붙인다. */
    private fun buildTags(scoredRoute: ScoredRoute): List<RouteTag> = buildList {
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
        val generatedAt: String? = facilitySource.freshness?.generatedAt
        val basedOn: String? = if (generatedAt != null && generatedAt.length >= YEAR_MONTH_LENGTH) {
            generatedAt.substring(0, YEAR_MONTH_LENGTH).replace('-', '.')
        } else {
            null
        }
        return DataProvenance(kind = "live", basedOn = basedOn, origin = DATA_ORIGIN)
    }

    private companion object {

        const val DATA_ORIGIN: String = "공공데이터포털·서울 열린데이터광장"

        const val YEAR_MONTH_LENGTH: Int = 7

        const val TAG_CCTV_DENSITY_MIN_PER_KM: Double = 5.0

        const val TAG_LAMP_DENSITY_MIN_PER_KM: Double = 10.0

        const val TAG_SAFE_PATH_OVERLAP_MIN: Double = 0.2
    }
}
