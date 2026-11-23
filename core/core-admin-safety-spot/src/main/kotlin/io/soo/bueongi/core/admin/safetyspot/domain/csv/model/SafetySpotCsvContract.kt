package io.soo.bueongi.core.admin.safetyspot.domain.csv.model

import io.soo.bueongi.core.admin.safetyspot.domain.csv.bell.BellCsvContract
import io.soo.bueongi.core.admin.safetyspot.domain.csv.cctv.CctvCsvContract
import io.soo.bueongi.core.admin.safetyspot.domain.csv.crimepronearea.CrimeProneAreaCsvContract
import io.soo.bueongi.core.admin.safetyspot.domain.csv.lamp.LampCsvContract
import io.soo.bueongi.core.admin.safetyspot.domain.csv.pedestriansignal.PedestrianSignalCsvContract
import io.soo.bueongi.core.admin.safetyspot.domain.csv.police.PoliceCsvContract
import io.soo.bueongi.core.admin.safetyspot.domain.csv.safehouse.SafeHouseCsvContract
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowResult
import io.soo.bueongi.storage.db.safetyspot.store.CsvSkipReason
import io.soo.bueongi.support.util.Epsg5174
import io.soo.bueongi.support.util.Epsg5179
import io.soo.bueongi.support.util.Wgs84Point

// 시설 한 종류의 업로드 CSV 계약 — 어떤 헤더를 읽고, 어떤 좌표계로 바꾸고, 어떤 행을 거르는지.
// 종류별 내용은 domain/csv/<시설명>/ 하위 클래스가 갖는다. 헤더 후보·좌표계 값은 pipeline sources.yaml 의 keyColumns 와 일치시킨다.
abstract class SafetySpotCsvContract(
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
    // 개방자치단체코드(7자리) 헤더만 받는다 — 행정표준 시군구코드(5자리)는 체계가 달라
    // 지역 조회(RegionLocator → 보안등 API)와 섞이면 어긋나므로 후보에 넣지 않는다.
    private val regionCodeHeaders: List<String> = listOf("개방자치단체코드", "제공기관코드"),
) {
    fun locateColumns(headerIndex: Map<String, Int>): CsvColumns {
        val latIdx: Int? = headerIndex.firstIndexOf(latHeaders)
        val lngIdx: Int? = headerIndex.firstIndexOf(lngHeaders)
        val hasLatLng: Boolean = latIdx != null && lngIdx != null
        return CsvColumns(
            hasLatLng = hasLatLng,
            coordSystem = if (hasLatLng) SafetySpotCoordSystem.WGS84 else coordSystem,
            latIndex = latIdx,
            lngIndex = lngIdx,
            xIndex = headerIndex.firstIndexOf(xHeaders),
            yIndex = headerIndex.firstIndexOf(yHeaders),
            nameIndex = headerIndex.firstIndexOf(nameHeaders),
            addressIndex = headerIndex.firstIndexOf(addressHeaders),
            purposeIndex = headerIndex.firstIndexOf(purposeHeaders),
            cameraCountIndex = headerIndex.firstIndexOf(cameraCountHeaders),
            phoneIndex = headerIndex.firstIndexOf(phoneHeaders),
            regionCodeIndex = headerIndex.firstIndexOf(regionCodeHeaders),
        )
    }

    fun judge(row: List<String>, columns: CsvColumns): CsvRowResult {
        val geo: GeoPoint = readGeoPoint(row, columns) ?: return CsvRowResult.Skipped(CsvSkipReason.NO_COORDINATE)
        val skip: CsvSkipReason? = skipReasonOf(row, columns)
        if (skip != null) {
            return CsvRowResult.Skipped(skip)
        }
        val point: SafetySpotPoint =
            SafetySpotPoint.of(
                type = type,
                lat = geo.lat,
                lng = geo.lng,
                name = columns.name(row),
                purpose = columns.purpose(row),
                cameraCount = columns.cameraCount(row),
                address = columns.address(row),
                phone = columns.phone(row),
                regionCode = columns.regionCode(row),
            )
        return CsvRowResult.Loaded(point)
    }

    // 종류 전용 거름 규칙 — 기본은 거르지 않는다.
    protected open fun skipReasonOf(row: List<String>, columns: CsvColumns): CsvSkipReason? {
        return null
    }

    private fun readGeoPoint(row: List<String>, columns: CsvColumns): GeoPoint? {
        if (columns.hasLatLng) {
            return GeoPoint.orNull(
                columns.latitude(row),
                columns.longitude(row),
            )
        }
        return projectXy(
            columns.projectedX(row),
            columns.projectedY(row),
        )
    }

    private fun projectXy(x: Double?, y: Double?): GeoPoint? {
        if (x == null || y == null) {
            return null
        }
        val wgs: Wgs84Point? = toWgs84(x, y)
        return GeoPoint.orNull(wgs?.lat, wgs?.lng)
    }

    private fun toWgs84(x: Double, y: Double): Wgs84Point? =
        when (coordSystem) {
            SafetySpotCoordSystem.EPSG5179 -> Epsg5179.toWgs84(x, y)
            SafetySpotCoordSystem.EPSG5174 -> Epsg5174.toWgs84(x, y)
            SafetySpotCoordSystem.WGS84 -> null
        }

    private fun Map<String, Int>.firstIndexOf(candidates: List<String>): Int? {
        for (candidate in candidates) {
            val index: Int? = this[candidate]
            if (index != null) {
                return index
            }
        }
        return null
    }

    companion object {
        fun forType(type: SafetySpotType): SafetySpotCsvContract =
            when (type) {
                SafetySpotType.CCTV -> CctvCsvContract()
                SafetySpotType.STREET_LAMP, SafetySpotType.SECURITY_LAMP -> LampCsvContract(type)
                SafetySpotType.SAFEHOUSE -> SafeHouseCsvContract()
                SafetySpotType.POLICE -> PoliceCsvContract()
                SafetySpotType.BELL -> BellCsvContract()
                SafetySpotType.PEDESTRIAN_SIGNAL -> PedestrianSignalCsvContract()
                SafetySpotType.CRIME_PRONE_AREA -> CrimeProneAreaCsvContract()
            }
    }
}
