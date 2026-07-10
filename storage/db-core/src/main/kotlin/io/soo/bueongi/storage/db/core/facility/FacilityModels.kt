package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

/**
 * 안심시설 저장 모델(인메모리). 공공데이터(CDN) 시설/안심귀갓길을 적재해
 * 공간 인덱스·안전 점수·우회 계산의 입력이 된다. 모든 좌표는 WGS84 {lat, lng}.
 * (시설 종류 어휘는 core-enum 의 [FacilityType] — 전 계층 공용.)
 */

/**
 * 위경도 한 점(비null 보장 — 공간 인덱스/점수/우회 내부 계산용).
 * 주의: 상위(core-api)의 RouteOption.path 가 이 타입을 그대로 직렬화한다 — 프로퍼티명(lat/lng)은
 * 프론트 응답 계약이므로 리네임 금지(계약 고정 테스트: core-api RouteOptionWireContractTest).
 */
data class GeoPoint(val lat: Double, val lng: Double) {
    companion object {
        /**
         * 좌표 유효성 불변식(유한값 + 위도 ±90 / 경도 ±180)의 단일 출처 팩토리 — 통과 못 하면 null.
         * 외부 데이터(CSV/CDN/공공 API/쿼리 파라미터)에서 좌표를 만들 땐 이 팩토리를 거친다 —
         * 같은 검사를 지점마다 복붙하면 한쪽만 고쳐지는 드리프트가 생기므로 규칙을 타입 안에 둔다.
         */
        fun orNull(lat: Double?, lng: Double?): GeoPoint? {
            if (lat == null || !lat.isFinite() || lat !in -90.0..90.0) return null
            if (lng == null || !lng.isFinite() || lng !in -180.0..180.0) return null
            return GeoPoint(lat, lng)
        }
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
 * 안심시설 데이터 신선도(P0-3). 파이프라인 산출물 manifest.json 의 생성 시점 —
 * "이 점수/마커가 언제 기준의 공공데이터로 계산됐는가"를 사용자에게 표기하기 위한 메타.
 */
data class FacilityFreshness(
    /** manifest.generatedAt(ISO-8601). 미상이면 null. */
    val generatedAt: String? = null,
    /** manifest.version(예: "2026-06-19"). 미상이면 null. */
    val version: String? = null,
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

    /** 적재 데이터의 신선도(manifest 기반). 미상이면 null — 표기를 생략할 뿐 기능엔 영향 없다. */
    val freshness: FacilityFreshness?
        get() = null
}

/**
 * 시설 라이브 갱신 싱크 — 도메인 갱신 서비스(core-api FacilityRefreshService)가 공공데이터 API 수집분으로
 * 종류별 인덱스를 교체할 때 쓰는 좁은 계약. [FacilityRepository] 가 유일 구현이며, 인터페이스로 분리해
 * 갱신 오케스트레이션을 파일 적재 없이 가짜 싱크로 단위 테스트할 수 있게 한다.
 */
interface FacilityLiveStore {
    /**
     * [type] 의 공간 인덱스를 [points] 로 **원자적으로 교체**한다(다른 종류·안심귀갓길은 불변).
     * 조회 중인 스레드는 교체 전/후 어느 한쪽의 일관된 스냅샷을 본다. 반환 = 교체된 건수.
     */
    fun replace(type: FacilityType, points: List<FacilityPoint>): Int
}
