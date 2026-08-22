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
        val penalized: Double = applyRiskPenalty(normalized, counts.crimeProneArea, km)

        return SafetyScore(
            score = (penalized * 100).roundToInt(),
            breakdown = SafetyBreakdown(
                routeKm = round3(routeKm),
                cctvDensity = round2(counts.cctv / km),
                lampDensity = round2(counts.lamp / km),
                bellDensity = round2(counts.bell / km),
                safehouseCount = counts.safehouse,
                policeCount = counts.police,
                safePathOverlap = round3(safePathOverlap),
                crimeProneAreaCount = counts.crimeProneArea,
            ),
        )
    }

    private fun countNearby(path: List<GeoPoint>): SafetySpotCounts =
        SafetySpotCounts(
            cctv = countWithinCorridor(SafetySpotType.CCTV, path),
            // 가로등과 보안등은 관리 주체만 다른 같은 조명이다 — 밝기 점수는 합쳐서 센다.
            lamp = countWithinCorridor(SafetySpotType.STREET_LAMP, path) +
                countWithinCorridor(SafetySpotType.SECURITY_LAMP, path),
            bell = countWithinCorridor(SafetySpotType.BELL, path),
            safehouse = countWithinCorridor(SafetySpotType.SAFEHOUSE, path),
            police = countWithinCorridor(SafetySpotType.POLICE, path),
            crimeProneArea = countRiskNear(path),
        )

    private fun countWithinCorridor(type: SafetySpotType, path: List<GeoPoint>): Int =
        safetySpots.index(type).withinCorridor(path, DEFAULT_CORRIDOR_METERS).size

    /**
     * 우범지역은 시설이 아니라 **구역**이라 더 넓게 본다.
     * 원천이 구역을 대표 좌표 하나로 주기 때문에, 시설과 같은 30m 로 재면 바로 옆을 지나도 안 걸린다.
     */
    private fun countRiskNear(path: List<GeoPoint>): Int =
        safetySpots.index(SafetySpotType.CRIME_PRONE_AREA).withinCorridor(path, RISK_CORRIDOR_METERS).size

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

    /**
     * 우범지역만큼 점수를 깎는다. 더하는 쪽(가중치 합 1.0)과 섞지 않고 뒤에서 빼는 이유는,
     * 시설이 아무리 많아도 우범지역을 지나면 그만큼 위험하다는 것이 별개의 사실이기 때문이다.
     *
     * 0 아래로는 내려가지 않는다 — 점수는 0~100 계약이다.
     */
    private fun applyRiskPenalty(normalized: Double, riskCount: Int, km: Double): Double {
        if (riskCount <= 0) {
            return normalized
        }
        val penalty: Double = MAX_RISK_PENALTY * saturate(riskCount / km, SAT_RISK_PER_KM)
        return max(0.0, normalized - penalty)
    }

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

        const val WEIGHT_CCTV: Double = 0.25

        /**
         * 조명(가로등+보안등) 가중치. 0.20 → 0.25 로 올렸다(2026-08-22, 사용자 요청 "보안등 점수를 높여라").
         *
         * 올린 만큼을 CCTV 에서 뺀 이유는 실측이다 — 강남역→홍대입구 13.2km 에서 조명은 14.89/km 로
         * 포화 기준(40)의 37% 를 실제로 채우는데, CCTV 는 2.57/km 로 기준(20)의 13% 밖에 못 채운다.
         * 가장 무거운 가중치가 거의 안 실리는 축에 놓여 있어, 데이터가 실제로 있는 축으로 옮겼다.
         * 합은 1.0 그대로라 0~100 계약은 변하지 않는다.
         */
        const val WEIGHT_LAMP: Double = 0.25
        const val WEIGHT_BELL: Double = 0.18
        const val WEIGHT_SAFEHOUSE: Double = 0.07
        const val WEIGHT_POLICE: Double = 0.10
        const val WEIGHT_SAFE_PATH: Double = 0.15

        /** 구역을 대표 좌표 하나로 받아 시설(30m)보다 넓게 본다. */
        const val RISK_CORRIDOR_METERS: Double = 100.0

        /** km 당 이만큼 있으면 감점이 최대에 이른다. */
        const val SAT_RISK_PER_KM: Double = 2.0

        /** 감점 상한 — 조명 가중치와 같은 크기다. 우범지역이 있다고 점수를 0 으로 만들지는 않는다. */
        const val MAX_RISK_PENALTY: Double = 0.20
    }
}
