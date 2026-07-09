package io.soo.bueongi.core.enums

/**
 * 안심시설 종류 — 전 계층(storage 적재·domain 점수·api 마커)이 공유하는 도메인 어휘.
 * CDN 컬렉션(type)과 매핑된다:
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
