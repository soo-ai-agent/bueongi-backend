package io.soo.bueongi.facility

/**
 * 안심 라우팅 도메인 모델(인메모리). 공공데이터(CDN) 시설/안심귀갓길을 백엔드가 적재해
 * 공간 인덱스·안전 점수·우회 계산에 쓴다. 모든 좌표는 WGS84 {lat, lng}.
 */

/** 위경도 한 점(비null 보장 — 공간 인덱스/점수/우회 내부 계산용). */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 안심시설 종류. CDN 컬렉션(type)과 매핑된다:
 * cctv=B-1, lamp(보안등)=C-2, bell(비상벨)=D-1/D-2, safehouse(여성안심지킴이집)=B-2, police=B-3/B-4.
 */
enum class FacilityType(val cdnType: String) {
    CCTV("cctv"),
    LAMP("lamp"),
    BELL("emergency_bell"),
    SAFEHOUSE("safehouse"),
    POLICE("police"),
    ;

    companion object {
        private val byCdnType = entries.associateBy { it.cdnType }

        /** CDN type 문자열 → 시설 종류. 미상이면 null(적재 시 건너뜀). */
        fun fromCdnType(value: String?): FacilityType? = value?.let { byCdnType[it] }
    }
}

/**
 * 시설 1건. 공간 인덱스/점수의 단위. name/sigungu/purpose/cameraCount 는 표시·디버깅용(점수 계산엔 좌표만 쓴다).
 * purpose(설치목적구분)·cameraCount(카메라대수)는 CCTV 전용 표시 메타 — 다른 유형은 null.
 */
data class FacilityPoint(
    val type: FacilityType,
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val sigungu: String? = null,
    val purpose: String? = null,
    val cameraCount: Int? = null,
    /** 소재지 주소(표시용). 데이터 결측 시 null. */
    val address: String? = null,
    /** 전화번호(지구대·파출소 등 표시·연결용). 데이터 결측 시 null. */
    val phone: String? = null,
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)
}

/**
 * 안심귀갓길 1건(좌표열). CDN safepath items 가 좌표열(coords)을 가지면 그대로,
 * 단일 점만 있으면 1점짜리 경로로 적재한다(겹침 계산에서 단일 점은 길이 0 → 보너스 0).
 */
data class SafePath(
    val coords: List<GeoPoint>,
    val name: String? = null,
)

/**
 * 안심시설 공간 질의 소스(점수/우회/마커가 의존하는 좁은 계약). [FacilityRepository] 가 유일 구현이며,
 * 인터페이스로 분리해 점수·우회 로직을 파일 적재 없이 가짜 소스로 단위 테스트할 수 있게 한다.
 */
interface FacilitySpatialSource {
    /** 시설 종류별 공간 인덱스. 미적재 종류는 빈 인덱스. */
    fun index(type: FacilityType): GridSpatialIndex

    /** 적재된 안심귀갓길 좌표열. */
    val safePaths: List<SafePath>
}
