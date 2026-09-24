package io.soo.bueongi.core.route.domain.safety.service
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.safety.model.SafetyAxis
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.core.route.domain.safety.model.SafetyDataCoverage
import io.soo.bueongi.core.route.domain.safety.model.SafetyScore
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCounts
import io.soo.bueongi.core.route.domain.safety.model.SafetyWeights
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.core.safetyspot.model.overlapMetersWith
import io.soo.bueongi.storage.db.core.safetyspot.model.pathLengthMeters
import io.soo.bueongi.storage.db.core.safetyspot.model.sampleEveryMeters
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
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
        val samples: List<GeoPoint> = paths.flatMap { it.sampleEveryMeters(SAMPLE_SPACING_METERS, MAX_SAMPLES) }
        val covered: MutableSet<SafetyAxis> = LinkedHashSet()
        for (axis in SafetyAxis.entries) {
            if (hasDataNear(axis, samples)) {
                covered.add(axis)
            }
        }
        return SafetyDataCoverage(covered)
    }

    fun score(path: List<GeoPoint>): SafetyScore = score(path, coverageOf(listOf(path)))

    fun score(path: List<GeoPoint>, coverage: SafetyDataCoverage): SafetyScore {
        val lengthM: Double = path.pathLengthMeters()
        val routeKm: Double = lengthM / 1000.0
        val km: Double =
            if (routeKm > 0) {
                routeKm
            } else {
                MIN_DIVISOR_KM
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

    private fun hasDataNear(axis: SafetyAxis, samples: List<GeoPoint>): Boolean {
        if (axis == SafetyAxis.SAFE_PATH) {
            return hasSafePathNear(samples)
        }
        for (type in spotTypesOf(axis)) {
            for (sample in samples) {
                if (safetySpots.index(type).withinRadius(sample, DATA_PRESENCE_RADIUS_METERS).isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasSafePathNear(samples: List<GeoPoint>): Boolean {
        for (safePath in safetySpots.safePaths) {
            for (coord in safePath.coords) {
                for (sample in samples) {
                    if (coord.distanceMetersTo(sample) <= DATA_PRESENCE_RADIUS_METERS) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun countNearby(path: List<GeoPoint>): SafetySpotCounts =
        SafetySpotCounts(
            cctv = countWithinCorridor(SafetySpotType.CCTV, path),
            lamp =
                countWithinCorridor(SafetySpotType.STREET_LAMP, path) +
                    countWithinCorridor(SafetySpotType.SECURITY_LAMP, path),
            bell = countWithinCorridor(SafetySpotType.BELL, path),
            safehouse = countWithinCorridor(SafetySpotType.SAFEHOUSE, path),
            police = countWithinCorridor(SafetySpotType.POLICE, path),
            crimeProneArea = countRiskNear(path),
        )

    private fun countWithinCorridor(type: SafetySpotType, path: List<GeoPoint>): Int =
        safetySpots.index(type).withinCorridor(path, DEFAULT_CORRIDOR_METERS).size

    private fun countRiskNear(path: List<GeoPoint>): Int =
        safetySpots.index(SafetySpotType.CRIME_PRONE_AREA).withinCorridor(path, RISK_CORRIDOR_METERS).size

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

    private fun fillsOf(counts: SafetySpotCounts, km: Double, safePathOverlap: Double): Map<SafetyAxis, Double> =
        mapOf(
            SafetyAxis.CCTV to fill(counts.cctv / km, HALF_CCTV_PER_KM),
            SafetyAxis.LAMP to fill(counts.lamp / km, HALF_LAMP_PER_KM),
            SafetyAxis.BELL to fill(counts.bell / km, HALF_BELL_PER_KM),
            SafetyAxis.SAFEHOUSE to fill(counts.safehouse.toDouble(), HALF_SAFEHOUSE_COUNT),
            SafetyAxis.POLICE to fill(counts.police.toDouble(), HALF_POLICE_COUNT),
            SafetyAxis.SAFE_PATH to safePathOverlap,
        )

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

    private fun spotTypesOf(axis: SafetyAxis): List<SafetySpotType> = SPOT_TYPES_BY_AXIS.getValue(axis)

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

    private fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0

    private fun round3(v: Double): Double = (v * 1000).roundToInt() / 1000.0

    companion object {
        const val DEFAULT_CORRIDOR_METERS: Double = 30.0

        // 실측(2026-08-24, 전국 무작위 1.5km 구간 300개): 자료가 실린 축은 반경 1km 안에 최소 3건, 안 실린 축은 3km 까지 0건.
        const val DATA_PRESENCE_RADIUS_METERS: Double = 1_000.0

        const val SAMPLE_SPACING_METERS: Double = 500.0

        const val MAX_SAMPLES: Int = 40

        private const val MIN_DIVISOR_KM: Double = 0.001

        // 아래 세 값은 실측(2026-08-24, 전국 무작위 1.5km 구간 300개) 비영 밀도 중앙값이다.
        const val HALF_CCTV_PER_KM: Double = 2.7

        const val HALF_LAMP_PER_KM: Double = 6.0

        const val HALF_BELL_PER_KM: Double = 1.3

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
