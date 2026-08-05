package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

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
            if (lat == null || !lat.isFinite() || lat !in -90.0..90.0) {
                return null
            }
            if (lng == null || !lng.isFinite() || lng !in -180.0..180.0) {
                return null
            }
            return GeoPoint(lat, lng)
        }
    }
}
