package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.RegionSpotTally
import io.soo.bueongi.storage.db.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotOriginCount
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.support.util.KOREA_MAX_LAT
import io.soo.bueongi.support.util.KOREA_MAX_LNG
import io.soo.bueongi.support.util.KOREA_MIN_LAT
import io.soo.bueongi.support.util.KOREA_MIN_LNG
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant

// 안심시설을 PostGIS 에 둔다. 거리 판정은 geography(구면, 미터)라 위도에 따라 칸 크기가 달라지는 문제가 없다.
// 출처 조각 교체는 지우고 넣기를 한 트랜잭션으로 묶어, 다른 요청은 옛 조각이나 새 조각 하나만 본다.
@Repository
class PostgisSafetySpotStore(
    private val jdbc: NamedParameterJdbcTemplate,
    private val transactions: TransactionTemplate,
    private val cdnFiles: SafetySpotCdnFiles,
    private val clock: Clock,
) : SafetySpotSpatialSource, SafetySpotLiveStore {
    private val log: Logger = LoggerFactory.getLogger(PostgisSafetySpotStore::class.java)

    override val safePaths: List<SafePath>
        get() = cdnFiles.safePaths

    override val freshness: SafetySpotFreshness?
        get() = cdnFiles.freshness

    override fun findWithinRadius(type: SafetySpotType, center: GeoPoint, radiusMeters: Double): List<SafetySpotPoint> {
        if (radiusMeters <= 0) {
            return emptyList()
        }
        return findNear(type, pointWkt(center), radiusMeters)
    }

    override fun existsWithinRadius(type: SafetySpotType, center: GeoPoint, radiusMeters: Double): Boolean {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("wkt", pointWkt(center))
                .addValue("meters", radiusMeters)
        val exists: Boolean? = jdbc.queryForObject(EXISTS_NEAR_SQL, params, Boolean::class.java)
        return exists == true
    }

    override fun findWithinCorridor(type: SafetySpotType, path: List<GeoPoint>, corridorMeters: Double): List<SafetySpotPoint> {
        if (path.isEmpty() || corridorMeters <= 0) {
            return emptyList()
        }
        return findNear(type, pathWkt(path), corridorMeters)
    }

    override fun findByRegion(type: SafetySpotType, regionCode: String): List<SafetySpotPoint> {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("regionCode", regionCode)
        return jdbc.query(FIND_BY_REGION_SQL, params, pointMapper(type))
    }

    override fun findNearestWithRegion(type: SafetySpotType, center: GeoPoint, maxMeters: Double): SafetySpotPoint? {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("wkt", pointWkt(center))
                .addValue("meters", maxMeters)
        return jdbc.query(FIND_NEAREST_WITH_REGION_SQL, params, pointMapper(type)).firstOrNull()
    }

    override fun existsInRegion(type: SafetySpotType, regionCode: String): Boolean {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("regionCode", regionCode)
        val exists: Boolean? = jdbc.queryForObject(EXISTS_IN_REGION_SQL, params, Boolean::class.java)
        return exists == true
    }

    override fun countByOrigin(type: SafetySpotType): SafetySpotOriginCount {
        val params: MapSqlParameterSource = MapSqlParameterSource().addValue("type", type.cdnType)
        val counts: List<SafetySpotOriginCount> =
            jdbc.query(COUNT_BY_ORIGIN_SQL, params) { rs: ResultSet, _: Int ->
                SafetySpotOriginCount(fileCount = rs.getInt("file_count"), apiCount = rs.getInt("api_count"))
            }
        return counts.single()
    }

    override fun tallyByRegion(): List<RegionSpotTally> {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("minLat", KOREA_MIN_LAT)
                .addValue("maxLat", KOREA_MAX_LAT)
                .addValue("minLng", KOREA_MIN_LNG)
                .addValue("maxLng", KOREA_MAX_LNG)
        val tallies: ArrayList<RegionSpotTally> = ArrayList()
        jdbc.query(TALLY_BY_REGION_SQL, params) { rs: ResultSet ->
            val type: SafetySpotType = SafetySpotType.ofCdnType(rs.getString("type")) ?: return@query
            tallies.add(
                RegionSpotTally(
                    regionCode = rs.getString("region_code"),
                    type = type,
                    total = rs.getInt("total"),
                    apiCount = rs.getInt("api_count"),
                    latSum = rs.getDouble("lat_sum"),
                    lngSum = rs.getDouble("lng_sum"),
                    locatedCount = rs.getInt("located_count"),
                ),
            )
        }
        return tallies
    }

    override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {
        transactions.executeWithoutResult {
            deleteSlice(type, UPLOAD_SOURCE)
            deleteSlice(type, CDN_SOURCE)
            insert(type, UPLOAD_SOURCE, points)
            // 업로드에 든 지역은 외부 API 조각을 쓰지 않는다 — 같은 시설을 좌표만 조금 달리 두 번 세지 않게.
            val params: MapSqlParameterSource = MapSqlParameterSource().addValue("type", type.cdnType)
            jdbc.update(DELETE_API_SLICES_COVERED_BY_UPLOAD_SQL, params)
            jdbc.update(DELETE_API_SLICE_RECORDS_COVERED_BY_UPLOAD_SQL, params)
            recordSlice(type, UPLOAD_SOURCE, points.size)
        }
        log.info("안심시설 업로드 교체: type={}, count={}", type.cdnType, points.size)
    }

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        val source: String = apiSource(regionCode)
        if (points.isEmpty()) {
            // 다시 받은 결과가 비어도 전에 받은 API 데이터는 지우지 않는다 — 저장 시각만 새로 해 곧바로 다시 받지 않게 한다.
            touchSlice(type, source)
            return
        }
        transactions.executeWithoutResult {
            deleteSlice(type, source)
            insert(type, source, points)
            recordSlice(type, source, points.size)
        }
        log.info("안심시설 지역 교체: type={}, region={}, count={}", type.cdnType, regionCode, points.size)
    }

    override fun savedAtOf(type: SafetySpotType, regionCode: String): Instant? {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("source", apiSource(regionCode))
        val savedAts: List<Instant> =
            jdbc.query(SLICE_SAVED_AT_SQL, params) { rs: ResultSet, _: Int ->
                rs.getTimestamp("saved_at").toInstant()
            }
        return savedAts.firstOrNull()
    }

    // 배포 CDN 파일은 그 종류에 업로드가 없을 때만 쓴다(업로드가 CDN 을 대신한다).
    fun replaceCdnUnlessUploaded(type: SafetySpotType, points: List<SafetySpotPoint>): Boolean {
        var replaced: Boolean = false
        transactions.executeWithoutResult {
            val params: MapSqlParameterSource =
                MapSqlParameterSource()
                    .addValue("type", type.cdnType)
                    .addValue("source", UPLOAD_SOURCE)
            val uploaded: Boolean? = jdbc.queryForObject(EXISTS_SLICE_SQL, params, Boolean::class.java)
            if (uploaded == true) {
                return@executeWithoutResult
            }
            deleteSlice(type, CDN_SOURCE)
            insert(type, CDN_SOURCE, points)
            recordSlice(type, CDN_SOURCE, points.size)
            replaced = true
        }
        return replaced
    }

    private fun findNear(type: SafetySpotType, wkt: String, meters: Double): List<SafetySpotPoint> {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("wkt", wkt)
                .addValue("meters", meters)
        return jdbc.query(FIND_NEAR_SQL, params, pointMapper(type))
    }

    private fun deleteSlice(type: SafetySpotType, source: String) {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("source", source)
        jdbc.update(DELETE_SLICE_SQL, params)
    }

    private fun insert(type: SafetySpotType, source: String, points: List<SafetySpotPoint>) {
        for (chunk in points.chunked(INSERT_BATCH_SIZE)) {
            val batch: Array<MapSqlParameterSource> = Array(chunk.size) { i -> insertParams(type, source, chunk[i]) }
            jdbc.batchUpdate(INSERT_SQL, batch)
        }
    }

    private fun insertParams(type: SafetySpotType, source: String, point: SafetySpotPoint): MapSqlParameterSource {
        return MapSqlParameterSource()
            .addValue("type", type.cdnType)
            .addValue("source", source)
            .addValue("regionCode", point.regionCodeOrNull())
            .addValue("name", point.name)
            .addValue("address", point.address)
            .addValue("phone", point.phoneOrNull())
            .addValue("purpose", point.purposeOrNull())
            .addValue("cameraCount", point.cameraCountOrNull())
            .addValue("lat", point.lat)
            .addValue("lng", point.lng)
    }

    private fun recordSlice(type: SafetySpotType, source: String, rowCount: Int) {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("source", source)
                .addValue("rowCount", rowCount)
                .addValue("savedAt", Timestamp.from(clock.instant()))
        jdbc.update(UPSERT_SLICE_SQL, params)
    }

    private fun touchSlice(type: SafetySpotType, source: String) {
        val params: MapSqlParameterSource =
            MapSqlParameterSource()
                .addValue("type", type.cdnType)
                .addValue("source", source)
                .addValue("savedAt", Timestamp.from(clock.instant()))
        jdbc.update(TOUCH_SLICE_SQL, params)
    }

    private fun pointMapper(type: SafetySpotType): RowMapper<SafetySpotPoint> {
        return RowMapper { rs: ResultSet, _: Int ->
            SafetySpotPoint.of(
                type = type,
                lat = rs.getDouble("lat"),
                lng = rs.getDouble("lng"),
                name = rs.getString("name"),
                regionCode = rs.getString("region_code"),
                address = rs.getString("address"),
                phone = rs.getString("phone"),
                purpose = rs.getString("purpose"),
                cameraCount = nullableInt(rs, "camera_count"),
            )
        }
    }

    private companion object {
        const val CDN_SOURCE: String = "cdn"
        const val UPLOAD_SOURCE: String = "upload"
        const val API_SOURCE_PREFIX: String = "api:"
        const val INSERT_BATCH_SIZE: Int = 1_000

        // 카메라 수는 CCTV 만 있고 나머지 종류는 NULL 이다.
        fun nullableInt(rs: ResultSet, column: String): Int? {
            val value: Int = rs.getInt(column)
            if (rs.wasNull()) {
                return null
            }
            return value
        }

        fun apiSource(regionCode: String): String {
            return API_SOURCE_PREFIX + regionCode
        }

        fun pointWkt(point: GeoPoint): String {
            return "SRID=4326;POINT(${point.lng} ${point.lat})"
        }

        fun pathWkt(path: List<GeoPoint>): String {
            if (path.size == 1) {
                return pointWkt(path[0])
            }
            val coords: StringBuilder = StringBuilder()
            for (point in path) {
                if (coords.isNotEmpty()) {
                    coords.append(',')
                }
                coords.append(point.lng).append(' ').append(point.lat)
            }
            return "SRID=4326;LINESTRING($coords)"
        }

        // 같은 좌표(소수 5자리)는 한 곳으로 — 외부 API 출처를 먼저 남긴다.
        const val SPOT_COLUMNS: String = "lat, lng, name, region_code, address, phone, purpose, camera_count"
        const val DEDUP_ORDER: String = "ORDER BY lat_e5, lng_e5, from_api DESC, id"

        const val FIND_NEAR_SQL: String =
            "SELECT DISTINCT ON (lat_e5, lng_e5) $SPOT_COLUMNS FROM safety_spot " +
                "WHERE type = :type AND ST_DWithin(geog, ST_GeogFromText(:wkt), :meters) $DEDUP_ORDER"

        const val EXISTS_NEAR_SQL: String =
            "SELECT EXISTS (SELECT 1 FROM safety_spot WHERE type = :type AND ST_DWithin(geog, ST_GeogFromText(:wkt), :meters))"

        const val FIND_BY_REGION_SQL: String =
            "SELECT DISTINCT ON (lat_e5, lng_e5) $SPOT_COLUMNS FROM safety_spot " +
                "WHERE type = :type AND coalesce(trim(region_code), '') = :regionCode $DEDUP_ORDER"

        const val FIND_NEAREST_WITH_REGION_SQL: String =
            "SELECT $SPOT_COLUMNS FROM safety_spot WHERE type = :type AND region_code <> '' " +
                "AND ST_DWithin(geog, ST_GeogFromText(:wkt), :meters) ORDER BY geog <-> ST_GeogFromText(:wkt) LIMIT 1"

        const val EXISTS_IN_REGION_SQL: String =
            "SELECT EXISTS (SELECT 1 FROM safety_spot WHERE type = :type AND region_code = :regionCode)"

        const val COUNT_BY_ORIGIN_SQL: String =
            "SELECT count(*) FILTER (WHERE NOT from_api) AS file_count, count(*) FILTER (WHERE from_api) AS api_count " +
                "FROM (SELECT DISTINCT ON (lat_e5, lng_e5) from_api FROM safety_spot WHERE type = :type $DEDUP_ORDER) d"

        const val TALLY_BY_REGION_SQL: String =
            "SELECT coalesce(region_code, '') AS region_code, type, count(*) AS total, " +
                "count(*) FILTER (WHERE from_api) AS api_count, " +
                "coalesce(sum(lat) FILTER (WHERE in_korea), 0) AS lat_sum, " +
                "coalesce(sum(lng) FILTER (WHERE in_korea), 0) AS lng_sum, " +
                "count(*) FILTER (WHERE in_korea) AS located_count " +
                "FROM (SELECT DISTINCT ON (type, lat_e5, lng_e5) region_code, type, lat, lng, from_api, " +
                "(lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng) AS in_korea " +
                "FROM safety_spot ORDER BY type, lat_e5, lng_e5, from_api DESC, id) d " +
                "GROUP BY 1, 2"

        const val DELETE_SLICE_SQL: String = "DELETE FROM safety_spot WHERE type = :type AND source = :source"

        const val INSERT_SQL: String =
            "INSERT INTO safety_spot (type, source, region_code, name, address, phone, purpose, camera_count, lat, lng) " +
                "VALUES (:type, :source, :regionCode, :name, :address, :phone, :purpose, :cameraCount, :lat, :lng)"

        const val DELETE_API_SLICES_COVERED_BY_UPLOAD_SQL: String =
            "DELETE FROM safety_spot WHERE type = :type AND from_api AND substr(source, 5) IN " +
                "(SELECT DISTINCT region_code FROM safety_spot WHERE type = :type AND source = 'upload' AND region_code IS NOT NULL)"

        const val DELETE_API_SLICE_RECORDS_COVERED_BY_UPLOAD_SQL: String =
            "DELETE FROM safety_spot_slice WHERE type = :type AND source LIKE 'api:%' AND substr(source, 5) IN " +
                "(SELECT DISTINCT region_code FROM safety_spot WHERE type = :type AND source = 'upload' AND region_code IS NOT NULL)"

        const val UPSERT_SLICE_SQL: String =
            "INSERT INTO safety_spot_slice (type, source, row_count, saved_at) VALUES (:type, :source, :rowCount, :savedAt) " +
                "ON CONFLICT (type, source) DO UPDATE SET row_count = EXCLUDED.row_count, saved_at = EXCLUDED.saved_at"

        const val TOUCH_SLICE_SQL: String =
            "INSERT INTO safety_spot_slice (type, source, row_count, saved_at) VALUES (:type, :source, 0, :savedAt) " +
                "ON CONFLICT (type, source) DO UPDATE SET saved_at = EXCLUDED.saved_at"

        const val SLICE_SAVED_AT_SQL: String =
            "SELECT saved_at FROM safety_spot_slice WHERE type = :type AND source = :source"

        const val EXISTS_SLICE_SQL: String =
            "SELECT EXISTS (SELECT 1 FROM safety_spot_slice WHERE type = :type AND source = :source)"
    }
}
