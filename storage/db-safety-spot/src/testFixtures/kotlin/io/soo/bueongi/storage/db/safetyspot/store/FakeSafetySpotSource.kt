package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.RegionSpotTally
import io.soo.bueongi.storage.db.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotOriginCount
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.support.util.isInKorea
import java.util.Collections
import java.util.IdentityHashMap

// PostGIS 구현과 같은 규칙(같은 좌표는 한 곳, 겹치면 API 출처 우선)을 메모리 격자 색인으로 흉내 내는 테스트 대역.
class FakeSafetySpotSource(
    points: Map<SafetySpotType, List<SafetySpotPoint>> = emptyMap(),
    override val safePaths: List<SafePath> = emptyList(),
    // API 지역 수집 출처로 칠 점들. 색인에는 points 와 합쳐 들어간다 — 실구현처럼 API 점이 앞이다.
    apiPoints: Map<SafetySpotType, List<SafetySpotPoint>> = emptyMap(),
    override val freshness: SafetySpotFreshness? = null,
) : SafetySpotSpatialSource {
    private val indexByType: Map<SafetySpotType, GridSpatialIndex>
    private val apiOrigin: MutableSet<SafetySpotPoint> = Collections.newSetFromMap(IdentityHashMap())

    init {
        val built: LinkedHashMap<SafetySpotType, GridSpatialIndex> = LinkedHashMap()
        for (type in points.keys + apiPoints.keys) {
            val api: List<SafetySpotPoint> = apiPoints[type].orEmpty()
            apiOrigin.addAll(api)
            built[type] = GridSpatialIndex(api + points[type].orEmpty())
        }
        indexByType = built
    }

    override fun findWithinRadius(type: SafetySpotType, center: GeoPoint, radiusMeters: Double): List<SafetySpotPoint> {
        return index(type).withinRadius(center, radiusMeters)
    }

    override fun existsWithinRadius(type: SafetySpotType, center: GeoPoint, radiusMeters: Double): Boolean {
        return index(type).withinRadius(center, radiusMeters).isNotEmpty()
    }

    override fun findWithinCorridor(type: SafetySpotType, path: List<GeoPoint>, corridorMeters: Double): List<SafetySpotPoint> {
        return index(type).withinCorridor(path, corridorMeters)
    }

    override fun findByRegion(type: SafetySpotType, regionCode: String): List<SafetySpotPoint> {
        val found: ArrayList<SafetySpotPoint> = ArrayList()
        for (point in index(type).all) {
            if (point.regionCodeOrNull().orEmpty().trim() == regionCode) {
                found.add(point)
            }
        }
        return found
    }

    override fun findNearestWithRegion(type: SafetySpotType, center: GeoPoint, maxMeters: Double): SafetySpotPoint? {
        var nearest: SafetySpotPoint? = null
        var nearestDistance: Double = Double.MAX_VALUE
        for (point in index(type).withinRadius(center, maxMeters)) {
            if (point.regionCodeOrNull().isNullOrBlank()) {
                continue
            }
            val distance: Double = center.distanceMetersTo(point.toGeoPoint())
            if (distance < nearestDistance) {
                nearest = point
                nearestDistance = distance
            }
        }
        return nearest
    }

    override fun existsInRegion(type: SafetySpotType, regionCode: String): Boolean {
        return index(type).regions.contains(regionCode)
    }

    override fun countByOrigin(type: SafetySpotType): SafetySpotOriginCount {
        var apiCount: Int = 0
        for (point in index(type).all) {
            if (apiOrigin.contains(point)) {
                apiCount++
            }
        }
        return SafetySpotOriginCount(fileCount = index(type).size - apiCount, apiCount = apiCount)
    }

    override fun tallyByRegion(): List<RegionSpotTally> {
        val tallies: ArrayList<RegionSpotTally> = ArrayList()
        for ((type, index) in indexByType) {
            val byRegion: LinkedHashMap<String, MutableList<SafetySpotPoint>> = LinkedHashMap()
            for (point in index.all) {
                byRegion.getOrPut(point.regionCodeOrNull().orEmpty()) { ArrayList() }.add(point)
            }
            for ((regionCode, regionPoints) in byRegion) {
                tallies.add(tallyOf(regionCode, type, regionPoints))
            }
        }
        return tallies
    }

    fun index(type: SafetySpotType): GridSpatialIndex {
        return indexByType[type] ?: EMPTY_INDEX
    }

    private fun tallyOf(regionCode: String, type: SafetySpotType, points: List<SafetySpotPoint>): RegionSpotTally {
        var apiCount: Int = 0
        var latSum: Double = 0.0
        var lngSum: Double = 0.0
        var located: Int = 0
        for (point in points) {
            if (apiOrigin.contains(point)) {
                apiCount++
            }
            if (isInKorea(point.lat, point.lng)) {
                latSum += point.lat
                lngSum += point.lng
                located++
            }
        }
        return RegionSpotTally(regionCode, type, points.size, apiCount, latSum, lngSum, located)
    }

    companion object {
        private val EMPTY_INDEX: GridSpatialIndex = GridSpatialIndex(emptyList())

        fun of(type: SafetySpotType, points: List<GeoPoint>): FakeSafetySpotSource {
            val spots: ArrayList<SafetySpotPoint> = ArrayList()
            for (point in points) {
                spots.add(SafetySpotPoint.of(type, point.lat, point.lng))
            }
            return FakeSafetySpotSource(mapOf(type to spots))
        }
    }
}
