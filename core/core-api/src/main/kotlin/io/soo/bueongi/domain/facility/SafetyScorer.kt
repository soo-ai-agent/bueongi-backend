package io.soo.bueongi.domain.facility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath

import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 안전 점수 산정(프론트 safetyScore.ts 의 Kotlin 이식).
 *
 * 경로 30m 버퍼(corridor) 안 CCTV/조명/비상벨/안심집 밀도(개수/km)와 안심귀갓길 겹침 비율을 가중 합산해
 * 0~100 점을 낸다. 밀도는 상한으로 정규화(0~1)하고 가중치를 곱한다. 입력 데이터가 비어도 0으로 수렴한다
 * (분모 폭주 금지). 같은 경로+같은 데이터 → 같은 점수(결정론적).
 */
@Component
class SafetyScorer(
    private val repository: FacilitySpatialSource,
) {

    /** 한 경로의 안전 점수와 세부 지표를 계산한다. 회랑 폭은 [DEFAULT_CORRIDOR_METERS] 고정(조절 요구 없음). */
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

    /** [value] 를 [saturationCeiling](만점=정규화 1.0 에 이르는 기준값, SAT_* 상수) 대비 0~1 로 정규화한다. */
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
        /** 지구대·파출소 개수 — 희소 시설이라 2곳이면 만점 기여. */
        const val SAT_POLICE_COUNT: Double = 2.0

        const val WEIGHT_CCTV: Double = 0.30
        const val WEIGHT_LAMP: Double = 0.20
        const val WEIGHT_BELL: Double = 0.18
        const val WEIGHT_SAFEHOUSE: Double = 0.07
        const val WEIGHT_POLICE: Double = 0.10
        const val WEIGHT_SAFE_PATH: Double = 0.15
    }
}

/** 안전 점수 세부 지표(프론트 SafetyScoreBreakdown 동형). */
data class SafetyBreakdown(
    val routeKm: Double,
    val cctvDensity: Double,
    val lampDensity: Double,
    val bellDensity: Double,
    val safehouseCount: Int,
    val policeCount: Int,
    val safePathOverlap: Double,
)

/** 0~100 정수 안전 점수 + 세부 지표. */
data class SafetyScore(
    val score: Int,
    val breakdown: SafetyBreakdown,
)
