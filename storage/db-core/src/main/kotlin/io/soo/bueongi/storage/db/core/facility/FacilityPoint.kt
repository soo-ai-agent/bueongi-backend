package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

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
