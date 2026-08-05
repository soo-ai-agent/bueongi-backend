package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 경로상 POI 1건. 프론트 MapMock POI(`{ type, x, y }`)와 호환되도록 정규화 좌표 x/y(0~100)를 주고,
 * 실지도 연동을 위해 lat/lng 도 함께 준다(프론트는 필요한 쪽만 사용).
 *
 * safehouse(여성안심지킴이집·B-2)는 지정 거점일 뿐 영업시간 정보가 없어, 프론트에서 별도 주의 문구로 안내한다.
 */
data class FacilityPoi(
    val type: FacilityPoiType,
    val x: Int,
    val y: Int,
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    /** CCTV 마커 표시용 설치목적구분(용도)·카메라대수. 다른 유형/데이터 결측 시 null. */
    val purpose: String? = null,
    val cameraCount: Int? = null,
    /** 소재지 주소(마커 클릭 정보 카드 표시용). 데이터 결측 시 null. */
    val address: String? = null,
    /** 전화번호(지구대·파출소 등 — 정보 카드에서 전화 연결). 데이터 결측 시 null. */
    val phone: String? = null,
)
