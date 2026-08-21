package io.soo.bueongi.core.safetyspot.domain.status.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionName
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Service

/**
 * 자치단체별로 어떤 시설이 얼마나 적재됐는지 센다.
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
        val countsByRegion: LinkedHashMap<String, MutableMap<SafetySpotType, Int>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            for (point in safetySpots.index(type).all) {
                val code: String = regionCodeOf(point)
                val byType: MutableMap<SafetySpotType, Int> = countsByRegion.getOrPut(code) { LinkedHashMap() }
                byType[type] = (byType[type] ?: 0) + 1
            }
        }
        val rows: ArrayList<RegionSafetySpotCount> = ArrayList(countsByRegion.size)
        for ((code, byType) in countsByRegion) {
            rows.add(rowOf(code, byType))
        }
        rows.sortByDescending { it.total }
        return rows
    }

    private fun regionCodeOf(point: SafetySpotPoint): String {
        val code: String? = point.sigungu?.trim()?.takeIf { it.isNotEmpty() }
        return code ?: UNKNOWN_REGION
    }

    private fun rowOf(code: String, byType: Map<SafetySpotType, Int>): RegionSafetySpotCount {
        val known: RegionName? = regionNames.findByCode(code)
        return RegionSafetySpotCount(
            code = code,
            sido = known?.sido.orEmpty(),
            name = known?.name ?: code,
            countsByType = byType,
            total = byType.values.sum(),
        )
    }

    private companion object {
        const val UNKNOWN_REGION: String = "unknown"
    }
}
