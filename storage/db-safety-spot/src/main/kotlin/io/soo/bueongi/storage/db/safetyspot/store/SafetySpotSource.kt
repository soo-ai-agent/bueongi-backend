package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.RegionTypeCount
import io.soo.bueongi.storage.db.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotOriginCount
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

// 안심시설 조회 창구. 공간 질의는 PostGIS 네이티브 SQL 이라 core 가 JTS·JDBC 타입을 모르게 질의 단위로 감춘다.
// 같은 좌표(소수 5자리)의 시설은 한 곳으로 돌려준다. 겹치면 외부 API 출처를 남긴다(파일보다 최신).
interface SafetySpotSource {
    fun findWithinRadius(type: SafetySpotType, center: GeoPoint, radiusMeters: Double): List<SafetySpotPoint>

    fun existsWithinRadius(type: SafetySpotType, center: GeoPoint, radiusMeters: Double): Boolean

    fun findAlongPath(type: SafetySpotType, path: List<GeoPoint>, withinMeters: Double): List<SafetySpotPoint>

    fun findByRegion(type: SafetySpotType, regionCode: String): List<SafetySpotPoint>

    // 지역 코드가 있는 가장 가까운 시설. maxMeters 안에 없으면 null.
    fun findNearestWithRegion(type: SafetySpotType, center: GeoPoint, maxMeters: Double): SafetySpotPoint?

    fun existsInRegion(type: SafetySpotType, regionCode: String): Boolean

    fun countByOrigin(type: SafetySpotType): SafetySpotOriginCount

    fun countByRegion(): List<RegionTypeCount>

    val safePaths: List<SafePath>

    // 배포 CDN 의 manifest 가 없으면 신선도를 표기하지 않는다.
    val freshness: SafetySpotFreshness?
}
