package io.soo.bueongi.core.route.domain.safety.service
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCounts
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.core.route.domain.safety.model.SafetyScore
import io.soo.bueongi.storage.db.core.safetyspot.model.overlapMetersWith
import io.soo.bueongi.storage.db.core.safetyspot.model.pathLengthMeters
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 개수가 아니라 km당 밀도로 센다 — 그래야 긴 경로가 길다는 이유만으로 유리해지지 않는다. */
@Component
class SafetyScorer(
    private val safetySpots: SafetySpotSpatialSource,
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

        val counts: SafetySpotCounts = countNearby(path)
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

    private fun countNearby(path: List<GeoPoint>): SafetySpotCounts =
        SafetySpotCounts(
        cctv = countWithinCorridor(SafetySpotType.CCTV, path),
        lamp = countWithinCorridor(SafetySpotType.LAMP, path),
        bell = countWithinCorridor(SafetySpotType.BELL, path),
        safehouse = countWithinCorridor(SafetySpotType.SAFEHOUSE, path),
        police = countWithinCorridor(SafetySpotType.POLICE, path),
    )

    private fun countWithinCorridor(type: SafetySpotType, path: List<GeoPoint>): Int =
        safetySpots.index(type).withinCorridor(path, DEFAULT_CORRIDOR_METERS).size

    /** 가장 많이 겹치는 한 노선만 본다 — 여러 노선을 더하면 같은 구간이 중복으로 셈된다. */
    private fun safePathOverlapRatio(path: List<GeoPoint>, lengthM: Double): Double {
        if (lengthM <= 0 || safetySpots.safePaths.isEmpty()) {
            return 0.0
        }
        var best: Double = 0.0
        for (safePath in safetySpots.safePaths) {
            if (safePath.coords.size < 2) {
                continue
            }
            val overlap: Double = path.overlapMetersWith(safePath.coords, DEFAULT_CORRIDOR_METERS)
            best = maxOf(best, overlap / lengthM)
        }
        return min(1.0, best)
    }

    /** 가중치 합은 1.0 이어야 점수가 0~100 을 벗어나지 않는다. */
    private fun normalizedScore(counts: SafetySpotCounts, km: Double, safePathOverlap: Double): Double =
        WEIGHT_CCTV * saturate(counts.cctv / km, SAT_CCTV_PER_KM) +
            WEIGHT_LAMP * saturate(counts.lamp / km, SAT_LAMP_PER_KM) +
            WEIGHT_BELL * saturate(counts.bell / km, SAT_BELL_PER_KM) +
            WEIGHT_SAFEHOUSE * saturate(counts.safehouse.toDouble(), SAT_SAFEHOUSE_COUNT) +
            WEIGHT_POLICE * saturate(counts.police.toDouble(), SAT_POLICE_COUNT) +
            WEIGHT_SAFE_PATH * safePathOverlap

    /** 기준치를 넘으면 더 늘어도 1 로 본다 — CCTV 100대가 20대보다 5배 안전하진 않다. */
    private fun saturate(value: Double, saturationCeiling: Double): Double {
        if (saturationCeiling <= 0) {
            return 0.0
        }
        return max(0.0, min(1.0, value / saturationCeiling))
    }

    private fun round2(v: Double): Double =
        (v * 100).roundToInt() / 100.0

    private fun round3(v: Double): Double =
        (v * 1000).roundToInt() / 1000.0

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
