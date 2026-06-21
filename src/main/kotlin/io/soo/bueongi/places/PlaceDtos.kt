package io.soo.bueongi.places

/**
 * 장소 검색 결과 1건. 프론트 `Destination { name, address }` 와 상위 호환 —
 * name/address 는 그대로 매핑되고, lat/lng 는 경로 비교(POST /api/routes/compare)의
 * destination 좌표로 바로 쓰도록 additive 로 덧붙인다(프론트가 필요 시 사용, 무시해도 무방).
 */
data class PlaceItem(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)
