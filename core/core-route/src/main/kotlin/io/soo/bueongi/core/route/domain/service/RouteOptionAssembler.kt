package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.api.response.DataProvenance
import io.soo.bueongi.core.route.api.response.RouteOption
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.model.RouteTagVariant
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Component

@Component
class RouteOptionAssembler(
    private val markerBuilder: RouteMarkerBuilder,
    private val safetySpotSource: SafetySpotSpatialSource,
    private val routeTypeCatalog: RouteTypeCatalog,
) {
    internal fun assemble(scoredRoutes: List<ScoredRoute>, origin: GeoPoint, destination: GeoPoint): List<RouteOption> {
        val generatedAt: String? = safetySpotSource.freshness?.generatedAt
        val provenance: DataProvenance = DataProvenance.from(generatedAt)
        val ordered: List<ScoredRoute> = scoredRoutes.sortedByDescending { it.score.rank }
        val options: ArrayList<RouteOption> = ArrayList(ordered.size)
        for ((index, scoredRoute) in ordered.withIndex()) {
            val name: String = routeTypeCatalog.displayOf(scoredRoute.type).scoredName
            val tags: List<RouteTag> = buildTags(scoredRoute)
            val markers: List<SafetySpotPoi> = markerBuilder.buildMarkers(origin, destination, scoredRoute.candidate.path)
            val option: RouteOption = RouteOption.from(index, scoredRoute, name, tags, markers, provenance)
            options.add(option)
        }
        return options
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

    private companion object {
        const val TAG_CCTV_DENSITY_MIN_PER_KM: Double = 5.0
        const val TAG_LAMP_DENSITY_MIN_PER_KM: Double = 10.0
        const val TAG_SAFE_PATH_OVERLAP_MIN: Double = 0.2
    }
}
