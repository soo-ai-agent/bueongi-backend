package io.soo.bueongi.domain.place

/**
 * 외부 지도 API 기반 장소 검색 소스(예: Kakao Local). API 키가 주입되지 않으면 [isEnabled]=false 로,
 * 호출자([PlaceSearchService])는 내장 시드 카탈로그로 폴백한다 — 키 없이도 결정론적으로 동작하는 MVP 계약 유지.
 *
 * 마커 인터페이스로 분리한 이유: 시드 소스([SeedPlaceProvider])와 빈 주입 시 타입 모호성 없이
 * "외부 소스" 슬롯을 명확히 구분하고, 실연동(Kakao 외) 추가 시 동일 자리를 교체할 수 있게 한다.
 */
interface RemotePlaceProvider : PlaceProvider {
    /** 외부 API 키가 설정되어 실검색이 가능한지. false 면 [search] 를 호출하지 말고 시드로 폴백한다. */
    val isEnabled: Boolean
}
