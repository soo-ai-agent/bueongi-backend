package io.soo.bueongi.storage.db.core.safetyspot.model

import io.soo.bueongi.core.enums.SafetySpotType

// 안심시설 한 곳. 종류마다 하위 타입이 있고, 자기 종류를 스스로 안다.
// 좌표 외의 칸은 원천(공공 API·CSV)마다 주는 것이 달라 전부 비어 있을 수 있다.
// 전화(phone)는 원천이 주는 세 종류(보안등·치안시설·지킴이집)만 갖는다.
// sigungu 의 없음은 판단에도 쓰인다 — 지역을 모르는 시설은 지역 찾기에서 빠진다(RegionLocator).
sealed class SafetySpotPoint {
    abstract val type: SafetySpotType
    abstract val lat: Double
    abstract val lng: Double
    abstract val name: String?
    abstract val sigungu: String?
    abstract val address: String?

    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)

    companion object {
        // 원천 파일·CSV 는 종류를 값으로 들고 있어 하위 타입을 골라 줄 자리가 필요하다.
        fun of(
            type: SafetySpotType,
            lat: Double,
            lng: Double,
            name: String? = null,
            sigungu: String? = null,
            address: String? = null,
            phone: String? = null,
            purpose: String? = null,
            cameraCount: Int? = null,
        ): SafetySpotPoint =
            when (type) {
                SafetySpotType.CCTV ->
                    CctvSpot(lat, lng, name, sigungu, address, purpose, cameraCount)
                SafetySpotType.STREET_LAMP -> StreetLampSpot(lat, lng, name, sigungu, address)
                SafetySpotType.SECURITY_LAMP -> SecurityLampSpot(lat, lng, name, sigungu, address, phone)
                SafetySpotType.BELL -> BellSpot(lat, lng, name, sigungu, address)
                SafetySpotType.SAFEHOUSE -> SafeHouseSpot(lat, lng, name, sigungu, address, phone)
                SafetySpotType.POLICE -> PoliceSpot(lat, lng, name, sigungu, address, phone)
                SafetySpotType.PEDESTRIAN_SIGNAL -> PedestrianSignalSpot(lat, lng, name, sigungu, address)
                SafetySpotType.CRIME_PRONE_AREA -> CrimeProneAreaSpot(lat, lng, name, sigungu, address)
            }
    }
}

// 설치 목적·카메라 대수는 CCTV 만 갖는다(원천 CSV 칸).
data class CctvSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val sigungu: String? = null,
    override val address: String? = null,
    val purpose: String? = null,
    val cameraCount: Int? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.CCTV
}

data class StreetLampSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val sigungu: String? = null,
    override val address: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.STREET_LAMP
}

data class SecurityLampSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val sigungu: String? = null,
    override val address: String? = null,
    val phone: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.SECURITY_LAMP
}

data class BellSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val sigungu: String? = null,
    override val address: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.BELL
}

data class SafeHouseSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val sigungu: String? = null,
    override val address: String? = null,
    val phone: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.SAFEHOUSE
}

data class PoliceSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val sigungu: String? = null,
    override val address: String? = null,
    val phone: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.POLICE
}

data class PedestrianSignalSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val sigungu: String? = null,
    override val address: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.PEDESTRIAN_SIGNAL
}

data class CrimeProneAreaSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val sigungu: String? = null,
    override val address: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.CRIME_PRONE_AREA
}

// 전화는 원천이 주는 세 종류만 갖는다 — 저장·마커가 종류를 묻지 않고 꺼내 쓰는 창구다.
fun SafetySpotPoint.phoneOrNull(): String? =
    when (this) {
        is SecurityLampSpot -> phone
        is PoliceSpot -> phone
        is SafeHouseSpot -> phone
        is CctvSpot, is StreetLampSpot, is BellSpot, is PedestrianSignalSpot, is CrimeProneAreaSpot -> null
    }
