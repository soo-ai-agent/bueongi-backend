package io.soo.bueongi.core.route.api.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.model.NavigationStep
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.domain.safety.model.SafetyScoreDetail
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.support.util.formatDistance
import kotlin.math.max
import kotlin.math.roundToInt

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RouteOption(
    val id: String,
    val name: String,
    val time: String,
    val dist: String,
    val desc: String,
    val tags: List<RouteTag>,
    val type: RouteType,
    val path: List<GeoPoint>? = null,
    val steps: List<NavigationStep>? = null,
    val score: Int? = null,
    val markers: List<SafetySpotPoi>? = null,
    val scoreDetail: SafetyScoreDetail? = null,
    val source: DataSource? = null,
) {
    companion object {
        internal fun from(
            index: Int,
            scoredRoute: ScoredRoute,
            name: String,
            tags: List<RouteTag>,
            markers: List<SafetySpotPoi>,
            source: DataSource,
        ): RouteOption {
            val candidate: RouteCandidate = scoredRoute.candidate
            val scoreDetail: SafetyScoreDetail = scoredRoute.score.scoreDetail
            val minutes: Int = max(1, (candidate.timeSeconds / 60.0).roundToInt())
            return RouteOption(
                id = "tmap-${candidate.searchOption}-$index",
                name = name,
                time = "${minutes}분",
                dist = formatDistance(candidate.distanceMeters),
                desc = describe(scoredRoute.score.score, scoreDetail),
                tags = tags,
                type = scoredRoute.type,
                path = candidate.path,
                steps = candidate.steps,
                score = scoredRoute.score.score,
                markers = markers,
                scoreDetail = scoreDetail,
                source = source,
            )
        }

        private fun describe(score: Int?, scoreDetail: SafetyScoreDetail): String {
            if (score == null) {
                return "이 지역 안심시설 자료가 없어 안전 점수를 낼 수 없습니다"
            }
            return "안전 점수 ${score}점 · CCTV ${scoreDetail.cctvDensity}/km"
        }
    }
}
