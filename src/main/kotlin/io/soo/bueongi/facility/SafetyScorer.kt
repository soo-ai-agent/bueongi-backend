package io.soo.bueongi.facility

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

    /** 한 경로의 안전 점수와 세부 지표를 계산한다. */
    fun score(path: List<GeoPoint>, corridorMeters: Double = DEFAULT_CORRIDOR_METERS): SafetyScore {
        val lengthM = SpatialMath.pathLengthMeters(path)
        val routeKm = lengthM / 1000.0
        // 0 길이 경로 방어: 밀도 분모를 최소 1m(0.001km)로 잡아 폭주를 막는다.
        val km = if (routeKm > 0) routeKm else 0.001

        val cctvCount = repository.index(FacilityType.CCTV).withinCorridor(path, corridorMeters).size
        val lampCount = repository.index(FacilityType.LAMP).withinCorridor(path, corridorMeters).size
        val bellCount = repository.index(FacilityType.BELL).withinCorridor(path, corridorMeters).size
        val safehouseCount = repository.index(FacilityType.SAFEHOUSE).withinCorridor(path, corridorMeters).size
        // 지구대·파출소도 안전 점수에 반영한다(긴급 도움처). 희소 시설이라 개수 기반으로 정규화.
        val policeCount = repository.index(FacilityType.POLICE).withinCorridor(path, corridorMeters).size

        val cctvDensity = cctvCount / km
        val lampDensity = lampCount / km
        val bellDensity = bellCount / km

        // 안심귀갓길 겹침 비율(0~1): 후보 경로 길이 대비 겹침 길이의 최댓값.
        var safePathOverlap = 0.0
        if (lengthM > 0 && repository.safePaths.isNotEmpty()) {
            for (sp in repository.safePaths) {
                if (sp.coords.size < 2) continue
                val overlap = SpatialMath.overlapLengthMeters(path, sp.coords, corridorMeters)
                val ratio = overlap / lengthM
                if (ratio > safePathOverlap) safePathOverlap = ratio
            }
            safePathOverlap = min(1.0, safePathOverlap)
        }

        val normalized =
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

    private fun saturate(value: Double, max: Double): Double {
        if (max <= 0) return 0.0
        return max(0.0, min(1.0, value / max))
    }

    private fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0
    private fun round3(v: Double): Double = (v * 1000).roundToInt() / 1000.0

    companion object {
        const val DEFAULT_CORRIDOR_METERS = 30.0

        // 각 밀도/개수가 만점(정규화 1.0)에 이르는 기준값(MVP 캘리브레이션).
        const val SAT_CCTV_PER_KM = 20.0
        const val SAT_LAMP_PER_KM = 40.0
        const val SAT_BELL_PER_KM = 5.0
        const val SAT_SAFEHOUSE_COUNT = 3.0
        /** 지구대·파출소 개수 — 희소 시설이라 2곳이면 만점 기여. */
        const val SAT_POLICE_COUNT = 2.0

        // 설계 가중치(합 1.0). 지구대·파출소(0.10)·여성안심지킴이집(0.07) 반영분만큼 CCTV/조명/비상벨을 재배분.
        const val WEIGHT_CCTV = 0.30
        const val WEIGHT_LAMP = 0.20
        const val WEIGHT_BELL = 0.18
        const val WEIGHT_SAFEHOUSE = 0.07
        const val WEIGHT_POLICE = 0.10
        const val WEIGHT_SAFE_PATH = 0.15
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
