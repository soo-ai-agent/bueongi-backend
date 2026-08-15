package io.soo.bueongi.core.domain.facility
import io.soo.bueongi.storage.db.core.facility.overlapMetersWith
import io.soo.bueongi.storage.db.core.facility.pathLengthMeters
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint

import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Component
class SafetyScorer(
    private val repository: FacilitySpatialSource,
) {

    fun score(path: List<GeoPoint>): SafetyScore {
        val lengthM: Double = path.pathLengthMeters()
        val routeKm: Double = lengthM / 1000.0
        // 0 나눗셈을 피하려고 아주 짧은 경로에 최소 길이를 준다(밀도 = 건수 / km).
        val km: Double = if (routeKm > 0) {
            routeKm
        } else {
            0.001
        }

        val counts: FacilityCounts = countNearby(path)
        val safePathOverlap: Double = safePathOverlapRatio(path, lengthM)
        val normalized: Double = normalizedScore(counts, km, safePathOverlap)

        return SafetyScore(
            score = (normalized * 100).roundToInt(),
            breakdown = SafetyBreakdown(
                routeKm = round3(routeKm),
                cctvDensity = round2(counts.cctv / km),
                lampDensity = round2(counts.lamp / km),
                bellDensity = round2(counts.bell / km),
                safehouseCount = counts.safehouse,
                policeCount = counts.police,
                safePathOverlap = round3(safePathOverlap),
            ),
        )
    }

    private fun countNearby(path: List<GeoPoint>): FacilityCounts = FacilityCounts(
        cctv = countWithinCorridor(FacilityType.CCTV, path),
        lamp = countWithinCorridor(FacilityType.LAMP, path),
        bell = countWithinCorridor(FacilityType.BELL, path),
        safehouse = countWithinCorridor(FacilityType.SAFEHOUSE, path),
        police = countWithinCorridor(FacilityType.POLICE, path),
    )

    private fun countWithinCorridor(type: FacilityType, path: List<GeoPoint>): Int =
        repository.index(type).withinCorridor(path, DEFAULT_CORRIDOR_METERS).size

    /** 안심귀갓길과 겹치는 비율(0~1). 가장 많이 겹치는 한 노선을 기준으로 한다. */
    private fun safePathOverlapRatio(path: List<GeoPoint>, lengthM: Double): Double {
        if (lengthM <= 0 || repository.safePaths.isEmpty()) {
            return 0.0
        }
        var best: Double = 0.0
        for (safePath in repository.safePaths) {
            if (safePath.coords.size < 2) {
                continue
            }
            val overlap: Double = path.overlapMetersWith(safePath.coords, DEFAULT_CORRIDOR_METERS)
            best = maxOf(best, overlap / lengthM)
        }
        return min(1.0, best)
    }

    private fun normalizedScore(counts: FacilityCounts, km: Double, safePathOverlap: Double): Double =
        WEIGHT_CCTV * saturate(counts.cctv / km, SAT_CCTV_PER_KM) +
            WEIGHT_LAMP * saturate(counts.lamp / km, SAT_LAMP_PER_KM) +
            WEIGHT_BELL * saturate(counts.bell / km, SAT_BELL_PER_KM) +
            WEIGHT_SAFEHOUSE * saturate(counts.safehouse.toDouble(), SAT_SAFEHOUSE_COUNT) +
            WEIGHT_POLICE * saturate(counts.police.toDouble(), SAT_POLICE_COUNT) +
            WEIGHT_SAFE_PATH * safePathOverlap

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

        const val SAT_CCTV_PER_KM: Double = 20.0
        const val SAT_LAMP_PER_KM: Double = 40.0
        const val SAT_BELL_PER_KM: Double = 5.0
        const val SAT_SAFEHOUSE_COUNT: Double = 3.0
        const val SAT_POLICE_COUNT: Double = 2.0

        const val WEIGHT_CCTV: Double = 0.30
        const val WEIGHT_LAMP: Double = 0.20
        const val WEIGHT_BELL: Double = 0.18
        const val WEIGHT_SAFEHOUSE: Double = 0.07
        const val WEIGHT_POLICE: Double = 0.10
        const val WEIGHT_SAFE_PATH: Double = 0.15
    }
}
