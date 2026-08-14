package io.soo.bueongi.facility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath

import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Component
class SafetyScorer(
    private val repository: FacilitySpatialSource,
) {

    fun score(path: List<GeoPoint>): SafetyScore {
        val corridorMeters: Double = DEFAULT_CORRIDOR_METERS
        val lengthM: Double = SpatialMath.pathLengthMeters(path)
        val routeKm: Double = lengthM / 1000.0
        val km: Double = if (routeKm > 0) {
            routeKm
        } else {
            0.001
        }

        val cctvCount: Int = repository.index(FacilityType.CCTV).withinCorridor(path, corridorMeters).size
        val lampCount: Int = repository.index(FacilityType.LAMP).withinCorridor(path, corridorMeters).size
        val bellCount: Int = repository.index(FacilityType.BELL).withinCorridor(path, corridorMeters).size
        val safehouseCount: Int = repository.index(FacilityType.SAFEHOUSE).withinCorridor(path, corridorMeters).size
        val policeCount: Int = repository.index(FacilityType.POLICE).withinCorridor(path, corridorMeters).size

        val cctvDensity: Double = cctvCount / km
        val lampDensity: Double = lampCount / km
        val bellDensity: Double = bellCount / km

        var safePathOverlap: Double = 0.0
        if (lengthM > 0 && repository.safePaths.isNotEmpty()) {
            for (safePath in repository.safePaths) {
                if (safePath.coords.size < 2) {
                    continue
                }
                val overlap: Double = SpatialMath.overlapLengthMeters(path, safePath.coords, corridorMeters)
                safePathOverlap = maxOf(safePathOverlap, overlap / lengthM)
            }
            safePathOverlap = min(1.0, safePathOverlap)
        }

        val normalized: Double =
            WEIGHT_CCTV * saturate(cctvDensity, SAT_CCTV_PER_KM) +
                WEIGHT_LAMP * saturate(lampDensity, SAT_LAMP_PER_KM) +
                WEIGHT_BELL * saturate(bellDensity, SAT_BELL_PER_KM) +
                WEIGHT_SAFEHOUSE * saturate(safehouseCount.toDouble(), SAT_SAFEHOUSE_COUNT) +
                WEIGHT_POLICE * saturate(policeCount.toDouble(), SAT_POLICE_COUNT) +
                WEIGHT_SAFE_PATH * safePathOverlap

        return SafetyScore(
            score = (normalized * 100).roundToInt(),
            breakdown = SafetyBreakdown(
                routeKm = round3(routeKm),
                cctvDensity = round2(cctvDensity),
                lampDensity = round2(lampDensity),
                bellDensity = round2(bellDensity),
                safehouseCount = safehouseCount,
                policeCount = policeCount,
                safePathOverlap = round3(safePathOverlap),
            ),
        )
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
