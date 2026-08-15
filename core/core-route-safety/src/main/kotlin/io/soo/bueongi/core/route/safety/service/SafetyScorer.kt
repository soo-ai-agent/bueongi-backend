package io.soo.bueongi.core.route.safety.service
import io.soo.bueongi.core.route.safety.model.FacilityCounts
import io.soo.bueongi.core.route.safety.model.SafetyBreakdown
import io.soo.bueongi.core.route.safety.model.SafetyScore
import io.soo.bueongi.storage.db.core.facility.overlapMetersWith
import io.soo.bueongi.storage.db.core.facility.pathLengthMeters
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint

import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 경로 하나의 안전 점수(0~100)를 낸다.
 *
 * 계산 얼개: 경로 양옆 [DEFAULT_CORRIDOR_METERS] 안의 시설을 종류별로 세고,
 * **km당 밀도**로 바꾼 뒤 종류별 포화 기준([SAT_*])으로 0~1 로 눌러 가중 합산한다.
 * 밀도를 쓰는 이유는 긴 경로가 개수만으로 유리해지지 않게 하기 위해서다.
 */
@Component
class SafetyScorer(
    private val facilities: FacilitySpatialSource,
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

        // 1. 회랑 안 시설 수를 종류별로 센다.
        val counts: FacilityCounts = countNearby(path)
        // 2. 지자체가 지정한 안심귀갓길과 얼마나 겹치는지(0~1) 구한다.
        val safePathOverlap: Double = safePathOverlapRatio(path, lengthM)
        // 3. 둘을 가중 합산해 0~1 로 만든다.
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
        facilities.index(type).withinCorridor(path, DEFAULT_CORRIDOR_METERS).size

    /** 안심귀갓길과 겹치는 비율(0~1). 가장 많이 겹치는 한 노선을 기준으로 한다. */
    private fun safePathOverlapRatio(path: List<GeoPoint>, lengthM: Double): Double {
        if (lengthM <= 0 || facilities.safePaths.isEmpty()) {
            return 0.0
        }
        var best: Double = 0.0
        for (safePath in facilities.safePaths) {
            if (safePath.coords.size < 2) {
                continue
            }
            val overlap: Double = path.overlapMetersWith(safePath.coords, DEFAULT_CORRIDOR_METERS)
            best = maxOf(best, overlap / lengthM)
        }
        return min(1.0, best)
    }

    /** 각 항목을 포화 기준으로 0~1 로 누른 뒤 가중치를 곱해 더한다(가중치 합 = 1.0). */
    private fun normalizedScore(counts: FacilityCounts, km: Double, safePathOverlap: Double): Double =
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
