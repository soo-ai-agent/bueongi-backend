package io.soo.bueongi.storage.db.safetyspot.model

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.bell.BellSpot
import io.soo.bueongi.storage.db.safetyspot.cctv.CctvSpot
import io.soo.bueongi.storage.db.safetyspot.crimepronearea.CrimeProneAreaSpot
import io.soo.bueongi.storage.db.safetyspot.pedestriansignal.PedestrianSignalSpot
import io.soo.bueongi.storage.db.safetyspot.police.PoliceSpot
import io.soo.bueongi.storage.db.safetyspot.safehouse.SafeHouseSpot
import io.soo.bueongi.storage.db.safetyspot.securitylamp.SecurityLampSpot
import io.soo.bueongi.storage.db.safetyspot.streetlamp.StreetLampSpot

// 좌표 외의 칸은 원천(공공 API·CSV)마다 주는 것이 달라 전부 비어 있을 수 있다.
// 지역 코드는 원천의 체계대로 두 필드로 나뉜다 — 개방자치단체코드(7자리, 예: 종로구 3000000)와
// 행정표준 시군구코드(5자리, 예: 종로구 11110).
// 두 체계를 한 필드에 섞으면 지역 조회(RegionLocator → 보안등 API)가 어긋난다.
abstract class SafetySpotPoint {
    abstract val type: SafetySpotType
    abstract val lat: Double
    abstract val lng: Double
    abstract val name: String?
    abstract val address: String?

    fun toGeoPoint(): GeoPoint {
        return GeoPoint(lat, lng)
    }

    // 체계가 중요한 자리(보안등 API 호출)는 이 창구가 아니라 CctvSpot.openLocalGovCode 를 직접 본다.
    abstract fun regionCodeOrNull(): String?

    abstract fun phoneOrNull(): String?

    companion object {
        fun of(
            type: SafetySpotType,
            lat: Double,
            lng: Double,
            name: String? = null,
            regionCode: String? = null,
            address: String? = null,
            phone: String? = null,
            purpose: String? = null,
            cameraCount: Int? = null,
        ): SafetySpotPoint {
            return when (type) {
                SafetySpotType.CCTV ->
                    CctvSpot(lat, lng, name, address, openLocalGovCode = regionCode, purpose = purpose, cameraCount = cameraCount)
                SafetySpotType.STREET_LAMP -> StreetLampSpot(lat, lng, name, address, standardSigunguCode = regionCode)
                SafetySpotType.SECURITY_LAMP -> SecurityLampSpot(lat, lng, name, address, phone = phone, openLocalGovCode = regionCode)
                SafetySpotType.BELL -> BellSpot(lat, lng, name, address, openLocalGovCode = regionCode)
                SafetySpotType.SAFEHOUSE -> SafeHouseSpot(lat, lng, name, address, phone = phone, openLocalGovCode = regionCode)
                SafetySpotType.POLICE -> PoliceSpot(lat, lng, name, address, phone = phone, standardSigunguCode = regionCode)
                SafetySpotType.PEDESTRIAN_SIGNAL -> PedestrianSignalSpot(lat, lng, name, address, openLocalGovCode = regionCode)
                SafetySpotType.CRIME_PRONE_AREA -> CrimeProneAreaSpot(lat, lng, name, address, openLocalGovCode = regionCode)
            }
        }
    }
}
