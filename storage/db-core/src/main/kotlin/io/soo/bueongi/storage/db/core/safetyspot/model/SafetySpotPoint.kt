package io.soo.bueongi.storage.db.core.safetyspot.model

import io.soo.bueongi.core.enums.SafetySpotType

// 안심시설 한 곳. 종류마다 하위 타입이 있고, 자기 종류를 스스로 안다.
// 좌표 외의 칸은 원천(공공 API·CSV)마다 주는 것이 달라 전부 비어 있을 수 있다.
// 전화(phone)는 원천이 주는 세 종류(보안등·치안시설·지킴이집)만 갖는다.
// 지역 코드는 원천의 체계대로 두 필드로 나뉜다 — 개방자치단체코드(7자리)와 행정표준 시군구코드(5자리).
// 두 체계를 한 필드에 섞으면 지역 조회(RegionLocator → 보안등 API)가 어긋난다.
sealed class SafetySpotPoint {
    abstract val type: SafetySpotType
    abstract val lat: Double
    abstract val lng: Double
    abstract val name: String?
    abstract val address: String?

    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)

    companion object {
        // 원천 파일·CSV 는 종류를 값으로 들고 있어 하위 타입을 골라 줄 자리가 필요하다.
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
        ): SafetySpotPoint =
            when (type) {
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

// 설치 목적·카메라 대수는 CCTV 만 갖는다(원천 CSV 칸).
data class CctvSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    // 개방자치단체코드 7자리 (예: 종로구 3000000)
    val openLocalGovCode: String? = null,
    val purpose: String? = null,
    val cameraCount: Int? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.CCTV
}

data class StreetLampSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    // 행정표준 시군구코드 5자리 (예: 종로구 11110)
    val standardSigunguCode: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.STREET_LAMP
}

data class SecurityLampSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    // 개방자치단체코드 7자리 (예: 종로구 3000000)
    val openLocalGovCode: String? = null,
    val phone: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.SECURITY_LAMP
}

data class BellSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    // 개방자치단체코드 7자리 (예: 종로구 3000000)
    val openLocalGovCode: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.BELL
}

data class SafeHouseSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    // 개방자치단체코드 7자리 (예: 종로구 3000000)
    val openLocalGovCode: String? = null,
    val phone: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.SAFEHOUSE
}

data class PoliceSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    // 행정표준 시군구코드 5자리 (예: 종로구 11110)
    val standardSigunguCode: String? = null,
    val phone: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.POLICE
}

data class PedestrianSignalSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    // 개방자치단체코드 7자리 (예: 종로구 3000000)
    val openLocalGovCode: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.PEDESTRIAN_SIGNAL
}

data class CrimeProneAreaSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    // 개방자치단체코드 7자리 (예: 종로구 3000000)
    val openLocalGovCode: String? = null,
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

// 체계를 가리지 않고 지역 코드를 꺼내는 창구 — 저장·집계·색인이 쓴다.
// 체계가 중요한 자리(보안등 API 호출)는 이 창구가 아니라 CctvSpot.openLocalGovCode 를 직접 본다.
fun SafetySpotPoint.regionCodeOrNull(): String? =
    when (this) {
        is CctvSpot -> openLocalGovCode
        is SecurityLampSpot -> openLocalGovCode
        is BellSpot -> openLocalGovCode
        is SafeHouseSpot -> openLocalGovCode
        is PedestrianSignalSpot -> openLocalGovCode
        is CrimeProneAreaSpot -> openLocalGovCode
        is StreetLampSpot -> standardSigunguCode
        is PoliceSpot -> standardSigunguCode
    }
