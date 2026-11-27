package io.soo.bueongi.core.admin.safetyspot.domain.csv.model

import io.soo.bueongi.core.admin.safetyspot.domain.csv.bell.BellCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.cctv.CctvCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.crimepronearea.CrimeProneAreaCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.lamp.LampCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.pedestriansignal.PedestrianSignalCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.police.PoliceCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.safehouse.SafeHouseCsvFormat
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowLoaded
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowResult
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowSkipped
import io.soo.bueongi.storage.db.safetyspot.store.CsvSkipReason
import io.soo.bueongi.support.util.Epsg5174
import io.soo.bueongi.support.util.Epsg5179
import io.soo.bueongi.support.util.Wgs84Point

// 시설 한 종류의 업로드 CSV 형식 — 어떤 머리글을 읽고, 어떤 좌표계로 바꾸고, 어떤 행을 빼는지.
// 종류별 내용은 domain/csv/<시설명>/ 의 하위 클래스가 갖는다. 머리글 이름은 원본 CSV 에 적힌 그대로다.
abstract class SafetySpotCsvFormat(
    val type: SafetySpotType,
    val coordSystem: SafetySpotCoordSystem,
    private val latHeaders: List<String> = listOf("위도"),
    private val lngHeaders: List<String> = listOf("경도"),
    private val xHeaders: List<String> = listOf("X좌표"),
    private val yHeaders: List<String> = listOf("Y좌표"),
    private val nameHeaders: List<String> = emptyList(),
    private val addressHeaders: List<String> = emptyList(),
    private val purposeHeaders: List<String> = emptyList(),
    private val cameraCountHeaders: List<String> = emptyList(),
    private val phoneHeaders: List<String> = emptyList(),
    // 개방자치단체코드(7자리) 머리글만 받는다 — 행정표준 시군구코드(5자리)는 체계가 달라
    // 지역 조회(RegionCodeFinder → 보안등 API)와 섞이면 어긋나므로 후보에 넣지 않는다.
    private val regionCodeHeaders: List<String> = listOf("개방자치단체코드", "제공기관코드"),
) {
    fun findColumns(header: List<String>): CsvColumns {
        val indexByHeader: Map<String, Int> = indexByHeader(header)
        val latIndex: Int? = firstIndex(indexByHeader, latHeaders)
        val lngIndex: Int? = firstIndex(indexByHeader, lngHeaders)
        val hasLatLng: Boolean = latIndex != null && lngIndex != null

        // 위도·경도 칸이 있으면 이 종류의 좌표계가 무엇이든 위경도(WGS84)로 읽는다.
        var usedCoordSystem: SafetySpotCoordSystem = coordSystem
        if (hasLatLng) {
            usedCoordSystem = SafetySpotCoordSystem.WGS84
        }

        return CsvColumns(
            hasLatLng = hasLatLng,
            coordSystem = usedCoordSystem,
            latIndex = latIndex,
            lngIndex = lngIndex,
            xIndex = firstIndex(indexByHeader, xHeaders),
            yIndex = firstIndex(indexByHeader, yHeaders),
            nameIndex = firstIndex(indexByHeader, nameHeaders),
            addressIndex = firstIndex(indexByHeader, addressHeaders),
            purposeIndex = firstIndex(indexByHeader, purposeHeaders),
            cameraCountIndex = firstIndex(indexByHeader, cameraCountHeaders),
            phoneIndex = firstIndex(indexByHeader, phoneHeaders),
            regionCodeIndex = firstIndex(indexByHeader, regionCodeHeaders),
        )
    }

    // cellRegionCodeFinder 가 null 이면 지역 코드 칸이 빈 행을 빈 채로 둔다 — 미리보기는 지역 코드를 쓰지 않는다.
    fun readRow(row: List<String>, columns: CsvColumns, cellRegionCodeFinder: CellRegionCodeFinder?): CsvRowResult {
        val location: GeoPoint = readLocation(row, columns) ?: return CsvRowSkipped(CsvSkipReason.NO_COORDINATE)
        val skipReason: CsvSkipReason? = skipReasonOf(row, columns)
        if (skipReason != null) {
            return CsvRowSkipped(skipReason)
        }
        val point: SafetySpotPoint =
            SafetySpotPoint.of(
                type = type,
                lat = location.lat,
                lng = location.lng,
                name = columns.name(row),
                purpose = columns.purpose(row),
                cameraCount = columns.cameraCount(row),
                address = columns.address(row),
                phone = columns.phone(row),
                regionCode = readRegionCode(row, columns, location, cellRegionCodeFinder),
            )
        return CsvRowLoaded(point)
    }

    // 종류마다 따로 빼는 행이 있으면 하위 클래스가 이유를 돌려준다. 기본은 빼지 않는다.
    protected open fun skipReasonOf(row: List<String>, columns: CsvColumns): CsvSkipReason? {
        return null
    }

    private fun readLocation(row: List<String>, columns: CsvColumns): GeoPoint? {
        if (columns.hasLatLng) {
            return GeoPoint.orNull(columns.latitude(row), columns.longitude(row))
        }
        val x: Double = columns.x(row) ?: return null
        val y: Double = columns.y(row) ?: return null
        val latLng: Wgs84Point = xyToLatLng(x, y) ?: return null
        return GeoPoint.orNull(latLng.lat, latLng.lng)
    }

    private fun xyToLatLng(x: Double, y: Double): Wgs84Point? {
        return when (coordSystem) {
            SafetySpotCoordSystem.EPSG5179 -> Epsg5179.toWgs84(x, y)
            SafetySpotCoordSystem.EPSG5174 -> Epsg5174.toWgs84(x, y)
            SafetySpotCoordSystem.WGS84 -> null
        }
    }

    // 지역 코드 칸이 없거나 빈 행은 좌표로 찾아 채운다 — 업로드한 지역을 외부 API 로 다시 받지 않게 하려는 것이다.
    private fun readRegionCode(
        row: List<String>,
        columns: CsvColumns,
        location: GeoPoint,
        cellRegionCodeFinder: CellRegionCodeFinder?,
    ): String? {
        val written: String? = columns.regionCode(row)
        if (written != null) {
            return written
        }
        if (cellRegionCodeFinder == null) {
            return null
        }
        return cellRegionCodeFinder.find(location)
    }

    // 같은 머리글이 두 번 나오면 앞의 것을 쓴다.
    private fun indexByHeader(header: List<String>): Map<String, Int> {
        val indexByHeader: LinkedHashMap<String, Int> = LinkedHashMap()
        for (index in header.indices) {
            val name: String = header[index].trim()
            if (name.isNotEmpty() && !indexByHeader.containsKey(name)) {
                indexByHeader[name] = index
            }
        }
        return indexByHeader
    }

    // 후보 머리글을 앞에서부터 찾아 처음 있는 것의 칸 번호를 돌려준다. 하나도 없으면 null 이다.
    private fun firstIndex(indexByHeader: Map<String, Int>, headers: List<String>): Int? {
        for (header in headers) {
            val index: Int? = indexByHeader[header]
            if (index != null) {
                return index
            }
        }
        return null
    }

    companion object {
        fun forType(type: SafetySpotType): SafetySpotCsvFormat {
            return when (type) {
                SafetySpotType.CCTV -> CctvCsvFormat()
                SafetySpotType.STREET_LAMP, SafetySpotType.SECURITY_LAMP -> LampCsvFormat(type)
                SafetySpotType.SAFEHOUSE -> SafeHouseCsvFormat()
                SafetySpotType.POLICE -> PoliceCsvFormat()
                SafetySpotType.BELL -> BellCsvFormat()
                SafetySpotType.PEDESTRIAN_SIGNAL -> PedestrianSignalCsvFormat()
                SafetySpotType.CRIME_PRONE_AREA -> CrimeProneAreaCsvFormat()
            }
        }
    }
}
