package io.soo.bueongi.core.route.domain.safety.service
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.safety.model.SafetyAxis
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.core.route.domain.safety.model.SafetyDataCoverage
import io.soo.bueongi.core.route.domain.safety.model.SafetyScore
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCounts
import io.soo.bueongi.core.route.domain.safety.model.SafetyWeights
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.safetyspot.model.overlapMetersWith
import io.soo.bueongi.storage.db.safetyspot.model.pathLengthMeters
import io.soo.bueongi.storage.db.safetyspot.model.pickPointsAlong
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.support.util.zonedAt
import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Component
class SafetyScorer(
    private val safetySpots: SafetySpotSpatialSource,
    private val weightsCatalog: SafetyWeightsCatalog = SafetyWeightsCatalog(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun coverageOf(paths: List<List<GeoPoint>>): SafetyDataCoverage {
        val scorePoints: List<GeoPoint> = paths.flatMap { it.pickPointsAlong(SCORE_POINT_SPACING_METERS, MAX_SCORE_POINTS) }
        val covered: MutableSet<SafetyAxis> = LinkedHashSet()
        for (axis in SafetyAxis.entries) {
            if (hasDataNear(axis, scorePoints)) {
                covered.add(axis)
            }
        }
        return SafetyDataCoverage(covered)
    }

    fun score(path: List<GeoPoint>): SafetyScore {
        val coverage: SafetyDataCoverage = coverageOf(listOf(path))
        return score(path, coverage)
    }

    fun score(path: List<GeoPoint>, coverage: SafetyDataCoverage): SafetyScore {
        val lengthM: Double = path.pathLengthMeters()
        val routeKm: Double = lengthM / 1000.0
        var km: Double = MIN_DIVISOR_KM
        if (routeKm > 0) {
            km = routeKm
        }

        val counts: SafetySpotCounts = countNearby(path)
        val safePathOverlap: Double = safePathOverlapRatio(path, lengthM)
        val weights: SafetyWeights = weightsCatalog.weightsAt(zonedAt(clock.millis()))
        val fills: Map<SafetyAxis, Double> = fillsOf(counts, km, safePathOverlap)
        val normalized: Double? = normalizedScore(fills, weights, coverage)
        val penalized: Double? = normalized?.let { applyRiskPenalty(it, counts.crimeProneArea, km) }

        return SafetyScore(
            score = penalized?.let { (it * 100).roundToInt() },
            breakdown =
                SafetyBreakdown(
                    routeKm = round3(routeKm),
                    cctvDensity = round2(counts.cctv / km),
                    lampDensity = round2(counts.lamp / km),
                    bellDensity = round2(counts.bell / km),
                    safehouseCount = counts.safehouse,
                    policeCount = counts.police,
                    safePathOverlap = round3(safePathOverlap),
                    crimeProneAreaCount = counts.crimeProneArea,
                    scoredAxes = coverage.covered.map { it.wireName },
                    unscoredAxes = coverage.missing.map { it.wireName },
                    axisCoverage = round3(coveredWeight(weights, coverage)),
                ),
        )
    }

    private fun hasDataNear(axis: SafetyAxis, scorePoints: List<GeoPoint>): Boolean {
        if (axis == SafetyAxis.SAFE_PATH) {
            return hasSafePathNear(scorePoints)
        }
        for (type in SPOT_TYPES_BY_AXIS.getValue(axis)) {
            for (point in scorePoints) {
                if (safetySpots.index(type).withinRadius(point, DATA_PRESENCE_RADIUS_METERS).isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasSafePathNear(scorePoints: List<GeoPoint>): Boolean {
        for (safePath in safetySpots.safePaths) {
            for (coord in safePath.coords) {
                for (point in scorePoints) {
                    if (coord.distanceMetersTo(point) <= DATA_PRESENCE_RADIUS_METERS) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun countNearby(path: List<GeoPoint>): SafetySpotCounts {
        return SafetySpotCounts(
            cctv = countWithinCorridor(SafetySpotType.CCTV, path),
            lamp =
                countWithinCorridor(SafetySpotType.STREET_LAMP, path) +
                    countWithinCorridor(SafetySpotType.SECURITY_LAMP, path),
            bell = countWithinCorridor(SafetySpotType.BELL, path),
            safehouse = countWithinCorridor(SafetySpotType.SAFEHOUSE, path),
            police = countWithinCorridor(SafetySpotType.POLICE, path),
            crimeProneArea = countRiskNear(path),
        )
    }

    private fun countWithinCorridor(type: SafetySpotType, path: List<GeoPoint>): Int {
        return safetySpots.index(type).withinCorridor(path, DEFAULT_CORRIDOR_METERS).size
    }

    private fun countRiskNear(path: List<GeoPoint>): Int {
        return safetySpots.index(SafetySpotType.CRIME_PRONE_AREA).withinCorridor(path, RISK_CORRIDOR_METERS).size
    }

    private fun safePathOverlapRatio(path: List<GeoPoint>, lengthM: Double): Double {
        if (lengthM <= 0 || safetySpots.safePaths.isEmpty()) {
            return 0.0
        }
        var bestSingleRouteOverlapRatio: Double = 0.0
        for (safePath: SafePath in safetySpots.safePaths) {
            if (safePath.coords.size < 2) {
                continue
            }
            val overlap: Double = path.overlapMetersWith(safePath.coords, DEFAULT_CORRIDOR_METERS)
            bestSingleRouteOverlapRatio = maxOf(bestSingleRouteOverlapRatio, overlap / lengthM)
        }
        return min(1.0, bestSingleRouteOverlapRatio)
    }

    private fun fillsOf(counts: SafetySpotCounts, km: Double, safePathOverlap: Double): Map<SafetyAxis, Double> {
        return mapOf(
            SafetyAxis.CCTV to densityAndCountFill(counts.cctv, km, HALF_CCTV_PER_KM),
            SafetyAxis.LAMP to densityAndCountFill(counts.lamp, km, HALF_LAMP_PER_KM),
            SafetyAxis.BELL to densityAndCountFill(counts.bell, km, HALF_BELL_PER_KM),
            SafetyAxis.SAFEHOUSE to fill(counts.safehouse.toDouble(), HALF_SAFEHOUSE_COUNT),
            SafetyAxis.POLICE to fill(counts.police.toDouble(), HALF_POLICE_COUNT),
            SafetyAxis.SAFE_PATH to safePathOverlap,
        )
    }

    // 밀도만 보면 시설을 더 많이 지나는 긴 경로가 밀도 희석으로 점수를 못 받는다 — 밀도 채움과 개수 채움을 반씩 섞는다.
    private fun densityAndCountFill(count: Int, km: Double, halfDensityPerKm: Double): Double {
        val densityFill: Double = fill(count / km, halfDensityPerKm)
        val countFill: Double = fill(count.toDouble(), halfDensityPerKm * HALF_COUNT_ROUTE_KM)
        return (densityFill + countFill) / 2
    }

    private fun normalizedScore(fills: Map<SafetyAxis, Double>, weights: SafetyWeights, coverage: SafetyDataCoverage): Double? {
        val totalWeight: Double = coveredWeight(weights, coverage)
        if (totalWeight <= 0) {
            return null
        }
        var weighted: Double = 0.0
        for (axis in SafetyAxis.entries) {
            if (!coverage.has(axis)) {
                continue
            }
            weighted += weights.byAxis.getValue(axis) * fills.getValue(axis)
        }
        return weighted / totalWeight
    }

    private fun coveredWeight(weights: SafetyWeights, coverage: SafetyDataCoverage): Double {
        var total: Double = 0.0
        for (axis in SafetyAxis.entries) {
            if (coverage.has(axis)) {
                total += weights.byAxis.getValue(axis)
            }
        }
        return total
    }

    private fun applyRiskPenalty(normalized: Double, riskCount: Int, km: Double): Double {
        if (riskCount <= 0) {
            return normalized
        }
        val penalty: Double = MAX_RISK_PENALTY * saturate(riskCount / km, SAT_RISK_PER_KM)
        return max(0.0, normalized - penalty)
    }

    private fun fill(value: Double, halfPoint: Double): Double {
        if (value <= 0 || halfPoint <= 0) {
            return 0.0
        }
        return value / (value + halfPoint)
    }

    private fun saturate(value: Double, saturationCeiling: Double): Double {
        if (saturationCeiling <= 0) {
            return 0.0
        }
        return max(0.0, min(1.0, value / saturationCeiling))
    }

    private fun round2(value: Double): Double {
        return (value * 100).roundToInt() / 100.0
    }

    private fun round3(value: Double): Double {
        return (value * 1000).roundToInt() / 1000.0
    }

    companion object {
        const val DEFAULT_CORRIDOR_METERS: Double = 30.0

        // 실측(2026-08-24, 전국 무작위 1.5km 구간 300개): 자료가 실린 축은 반경 1km 안에 최소 3건, 안 실린 축은 3km 까지 0건.
        const val DATA_PRESENCE_RADIUS_METERS: Double = 1_000.0

        const val SCORE_POINT_SPACING_METERS: Double = 500.0

        const val MAX_SCORE_POINTS: Int = 40

        private const val MIN_DIVISOR_KM: Double = 0.001

        // 아래 세 값은 실측(2026-08-24, 전국 무작위 1.5km 구간 300개) 비영 밀도 중앙값이다.
        const val HALF_CCTV_PER_KM: Double = 2.7

        const val HALF_LAMP_PER_KM: Double = 6.0

        const val HALF_BELL_PER_KM: Double = 1.3

        // 개수 반포화점 = 밀도 반포화점 × 실측 구간 길이 — 밀도와 같은 실측(1.5km 구간 300개)에서 파생한 값이다.
        const val HALF_COUNT_ROUTE_KM: Double = 1.5

        const val HALF_SAFEHOUSE_COUNT: Double = 1.0

        const val HALF_POLICE_COUNT: Double = 1.0

        const val RISK_CORRIDOR_METERS: Double = 100.0

        const val SAT_RISK_PER_KM: Double = 2.0

        const val MAX_RISK_PENALTY: Double = 0.20

        private val SPOT_TYPES_BY_AXIS: Map<SafetyAxis, List<SafetySpotType>> =
            mapOf(
                SafetyAxis.CCTV to listOf(SafetySpotType.CCTV),
                SafetyAxis.LAMP to listOf(SafetySpotType.STREET_LAMP, SafetySpotType.SECURITY_LAMP),
                SafetyAxis.BELL to listOf(SafetySpotType.BELL),
                SafetyAxis.SAFEHOUSE to listOf(SafetySpotType.SAFEHOUSE),
                SafetyAxis.POLICE to listOf(SafetySpotType.POLICE),
                SafetyAxis.SAFE_PATH to emptyList(),
            )
    }
}
