package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

/** 프로퍼티명(lat/lng)은 RouteOption.path 직렬화로 노출되는 프론트 응답 계약 — 리네임 금지. */
data class GeoPoint(val lat: Double, val lng: Double) {
    companion object {
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
