package io.soo.bueongi.core.safetyspot.domain.status.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.error.InvalidSpotLimitException
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionName
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpots
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.support.util.isInKorea
import org.springframework.stereotype.Service

/**
 * 자치단체별로 어떤 시설이 얼마나 적재됐는지 세고, 한 곳을 골라 그 지점들을 내준다.
 *
 * 지역을 모르는 지점(원천이 코드를 안 주는 CSV·CDN)은 [UNKNOWN_REGION] 한 줄로 모은다 —
 * 빼 버리면 합계가 전체 적재량과 안 맞아 "왜 적은가"를 다시 조사하게 된다.
 */
@Service
class RegionSafetySpotStatusService(
    private val safetySpots: SafetySpotSpatialSource,
    private val regionNames: RegionNameCatalog,
) {

    fun summarize(): List<RegionSafetySpotCount> {
        val talliesByRegion: LinkedHashMap<String, RegionTally> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            for (point in safetySpots.index(type).all) {
                val tally: RegionTally = talliesByRegion.getOrPut(regionCodeOf(point)) { RegionTally() }
                tally.add(type, point)
            }
        }
        val rows: ArrayList<RegionSafetySpotCount> = ArrayList(talliesByRegion.size)
        for ((code, tally) in talliesByRegion) {
            rows.add(rowOf(code, tally))
        }
        rows.sortByDescending { it.total }
        return rows
    }

    /**
     * 한 자치단체의 시설 지점. [limit] 을 넘는 지역은 잘라 내지만 [RegionSafetySpots.total] 로
     * 전체 건수를 함께 알려 준다 — 화면이 "몇 개 중 몇 개"라고 말할 수 있어야 한다.
     */
    fun findSpotsByCode(code: String, limit: Int): RegionSafetySpots {
        if (limit <= 0) {
            throw InvalidSpotLimitException(limit)
        }
        val budget: Int = limit.coerceAtMost(MAX_SPOTS)
        val pointsByType: LinkedHashMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            val inRegion: List<SafetySpotPoint> = safetySpots.index(type).all.filter { regionCodeOf(it) == code }
            if (inRegion.isNotEmpty()) {
                pointsByType[type] = inRegion
            }
        }
        val quotas: Map<SafetySpotType, Int> = quotasOf(pointsByType, budget)
        val shown: ArrayList<SafetySpotPoint> = ArrayList(budget)
        for ((type, points) in pointsByType) {
            shown.addAll(spreadEvenly(points, quotas.getValue(type)))
        }
        val region: RegionName = regionNameOf(code)
        return RegionSafetySpots(
            code = code,
            sido = region.sido,
            name = region.name,
            total = pointsByType.values.sumOf { it.size },
            spots = shown,
        )
    }

    /**
     * 종류마다 몇 개씩 보낼지 정한다.
     *
     * 종류를 순서대로 채우면 건수가 압도적인 한 종류(CCTV)가 상한을 다 먹어 나머지가 0개가 된다.
     * 한 자리씩 돌아가며 채워, 적은 종류부터 자기 몫을 다 받고 남는 자리를 많은 종류가 가져간다.
     */
    private fun quotasOf(
        pointsByType: Map<SafetySpotType, List<SafetySpotPoint>>,
        budget: Int,
    ): Map<SafetySpotType, Int> {
        val quotas: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
        for (type in pointsByType.keys) {
            quotas[type] = 0
        }
        var remaining: Int = budget
        while (remaining > 0) {
            var handedOut: Int = 0
            for ((type, points) in pointsByType) {
                if (remaining == 0) {
                    break
                }
                val given: Int = quotas.getValue(type)
                if (given >= points.size) {
                    continue
                }
                quotas[type] = given + 1
                remaining--
                handedOut++
            }
            if (handedOut == 0) {
                break
            }
        }
        return quotas
    }

    /**
     * 앞에서부터 자르면 원천 파일이 적힌 순서대로 한쪽 동네만 남는다.
     * 등간격으로 골라 지역 전체에 편다.
     */
    private fun spreadEvenly(points: List<SafetySpotPoint>, quota: Int): List<SafetySpotPoint> {
        if (quota >= points.size) {
            return points
        }
        val picked: ArrayList<SafetySpotPoint> = ArrayList(quota)
        for (slot in 0 until quota) {
            picked.add(points[slot * points.size / quota])
        }
        return picked
    }

    private fun regionCodeOf(point: SafetySpotPoint): String {
        val code: String? = point.sigungu?.trim()?.takeIf { it.isNotEmpty() }
        return code ?: UNKNOWN_REGION
    }

    private fun regionNameOf(code: String): RegionName {
        val known: RegionName? = regionNames.findByCode(code)
        if (known == null) {
            return RegionName(sido = "", name = code)
        }
        return known
    }

    private fun rowOf(code: String, tally: RegionTally): RegionSafetySpotCount {
        val region: RegionName = regionNameOf(code)
        return RegionSafetySpotCount(
            code = code,
            sido = region.sido,
            name = region.name,
            countsByType = tally.countsByType,
            total = tally.countsByType.values.sum(),
            center = centerOf(code, tally),
        )
    }

    /**
     * 지역을 모르는 줄에는 중심을 주지 않는다. 전국에 흩어진 점들의 평균이라 국토 한가운데를
     * 가리키는데, 그 자리에 아무것도 없다. 지도에 가장 큰 원으로 찍히면 거짓말이 된다.
     */
    private fun centerOf(code: String, tally: RegionTally): GeoPoint? {
        if (code == UNKNOWN_REGION) {
            return null
        }
        return tally.center()
    }

    /**
     * 한 자치단체의 집계 중간값.
     *
     * 세는 김에 중심 좌표까지 함께 더한다 — 중심을 따로 구하려면 전국 수십만 점을 한 번 더 훑어야 한다.
     */
    private class RegionTally {
        val countsByType: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
        private var latSum: Double = 0.0
        private var lngSum: Double = 0.0
        private var locatedCount: Int = 0

        fun add(type: SafetySpotType, point: SafetySpotPoint) {
            countsByType[type] = (countsByType[type] ?: 0) + 1
            if (!isInKorea(point.lat, point.lng)) {
                return
            }
            latSum += point.lat
            lngSum += point.lng
            locatedCount++
        }

        fun center(): GeoPoint? {
            if (locatedCount == 0) {
                return null
            }
            return GeoPoint(lat = latSum / locatedCount, lng = lngSum / locatedCount)
        }
    }

    private companion object {
        const val UNKNOWN_REGION: String = "unknown"

        /** 한 번에 내보낼 지점 수 상한. 응답 크기(한 점당 약 80바이트)와 지도 렌더 속도 때문에 자른다. */
        const val MAX_SPOTS: Int = 5_000
    }
}
