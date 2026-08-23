package io.soo.bueongi.core.route.domain.safety.service
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCounts
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.core.route.domain.safety.model.SafetyScore
import io.soo.bueongi.storage.db.core.safetyspot.model.overlapMetersWith
import io.soo.bueongi.storage.db.core.safetyspot.model.pathLengthMeters
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.core.route.domain.safety.model.SafetyWeights
import io.soo.bueongi.support.util.zonedAt

import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 개수가 아니라 km당 밀도로 센다 — 그래야 긴 경로가 길다는 이유만으로 유리해지지 않는다. */
@Component
class SafetyScorer(
    private val safetySpots: SafetySpotSpatialSource,
    private val weightsCatalog: SafetyWeightsCatalog = SafetyWeightsCatalog(),
    /** 밤낮 판정에 쓴다 — 테스트가 두 시간대를 모두 검증할 수 있게 주입받는다. */
    private val clock: Clock = Clock.systemUTC(),
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
        val weights: SafetyWeights = weightsCatalog.weightsAt(zonedAt(clock.millis()))
        val normalized: Double = normalizedScore(counts, km, safePathOverlap, weights)
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
    private fun normalizedScore(
        counts: SafetySpotCounts,
        km: Double,
        safePathOverlap: Double,
        weights: SafetyWeights,
    ): Double =
        weights.cctv * saturate(counts.cctv / km, SAT_CCTV_PER_KM) +
            weights.lamp * saturate(counts.lamp / km, SAT_LAMP_PER_KM) +
            weights.bell * saturate(counts.bell / km, SAT_BELL_PER_KM) +
            weights.safehouse * saturate(counts.safehouse.toDouble(), SAT_SAFEHOUSE_COUNT) +
            weights.police * saturate(counts.police.toDouble(), SAT_POLICE_COUNT) +
            weights.safePath * safePathOverlap

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

        /**
         * 포화 기준은 **실측 분포**에서 잡는다. 상한이 실제보다 높으면 모든 경로가 축의 아래쪽에
         * 몰려 그 항목이 길을 가르지 못한다 — 20/km 였을 때 서울 4개 구간 16개 후보의 CCTV 밀도는
         * 1.48~10.58/km(중앙값 2.82)라 대부분 13% 만 채우고 끝났다. 관측 최댓값(10.58)을 상한으로
         * 두면 같은 후보들이 15%~100% 로 퍼진다. 표본이 16개뿐이라 지역을 넓혀 재검토할 값이다.
         */
        const val SAT_CCTV_PER_KM: Double = 10.0

        /** 실측 0~15.17/km(p90 14.89). 40 은 조명이 깔린 구간조차 37% 에서 멈추게 했다. */
        const val SAT_LAMP_PER_KM: Double = 16.0

        /** 실측 0~5.76/km(p90 4.26). 5.0 은 관측 최댓값을 잘라 상위 구간이 뭉갰다. */
        const val SAT_BELL_PER_KM: Double = 6.0

        /** 실측 0 또는 2~3개. 상한 3 이면 2개와 3개가 갈린다 — 그대로 둔다. */
        const val SAT_SAFEHOUSE_COUNT: Double = 3.0

        /** 실측 0 또는 1개. 상한 2 는 아직 관측 범위를 덮는다 — 그대로 둔다. */
        const val SAT_POLICE_COUNT: Double = 2.0

        /** 구역을 대표 좌표 하나로 받아 시설(30m)보다 넓게 본다. */
        const val RISK_CORRIDOR_METERS: Double = 100.0

        /** km 당 이만큼 있으면 감점이 최대에 이른다. */
        const val SAT_RISK_PER_KM: Double = 2.0

        /**
         * 감점 상한. 우범지역이 있다고 점수를 0 으로 만들지는 않는다.
         *
         * 이 축은 원천이 전국 0건이라 아직 한 번도 발동한 적이 없다. 그래도 구조를 그대로 두는 이유는,
         * **더하는 쪽(합 1.0)의 밖에서 빼는 값**이라 데이터가 없는 동안 점수 폭을 한 뼘도 잡아먹지
         * 않기 때문이다(가중치를 나눠 갖던 안심귀갓길과 다른 점). 데이터가 들어오면 그날부터 그대로 작동한다.
         */
        const val MAX_RISK_PENALTY: Double = 0.20
    }
}
