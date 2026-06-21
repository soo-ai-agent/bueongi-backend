package io.soo.bueongi.routes

/**
 * 경로 유형. 직선거리(haversine)에 유형별 우회/단축 계수를 곱해 시간·거리를 만들고,
 * 안심시설 밀도(detour 가 클수록 CCTV·비상벨이 많은 길)를 결정한다. 결정론적.
 *
 * 안심 귀가 도메인: safe(가장 밝고 안전) → main(주요 도로) → fast(최단)로 갈수록 우회가 줄고 시설도 준다.
 */
enum class RouteType(
    val id: String,
    val apiType: String,
    val displayName: String,
    val desc: String,
    /** 직선거리 대비 실제 보행거리 계수(우회). */
    val distanceFactor: Double,
    /** km 당 안심시설 밀도(cctv, bell, store, police, safehouse). */
    val cctvPerKm: Double,
    val bellPerKm: Double,
    val storePerKm: Double,
    val policePerKm: Double,
    /** 여성안심지킴이집(B-2) 밀도. 지정 거점이라 희소 — 다른 시설보다 낮게 둔다. */
    val safehousePerKm: Double,
    val tags: List<RouteTag>,
) {
    SAFE(
        id = "1",
        apiType = "safe",
        displayName = "추천 경로",
        desc = "가장 밝고 안심 시설이 잘 갖춰진 길입니다.",
        distanceFactor = 1.18,
        cctvPerKm = 6.0, bellPerKm = 3.0, storePerKm = 2.0, policePerKm = 1.0, safehousePerKm = 1.0,
        tags = listOf(RouteTag("CCTV 많음", "mint"), RouteTag("밝은 길", "yellow")),
    ),
    MAIN(
        id = "2",
        apiType = "main",
        displayName = "주요 도로",
        desc = "사람이 많이 다니는 주요 도로 위주의 길입니다.",
        distanceFactor = 1.05,
        cctvPerKm = 4.0, bellPerKm = 1.5, storePerKm = 4.0, policePerKm = 0.5, safehousePerKm = 0.5,
        tags = listOf(RouteTag("주요 도로", "blue"), RouteTag("유동인구 많음", "default")),
    ),
    FAST(
        id = "3",
        apiType = "fast",
        displayName = "최단 경로",
        desc = "거리·시간이 가장 짧은 길입니다.",
        distanceFactor = 0.92,
        cctvPerKm = 2.0, bellPerKm = 0.5, storePerKm = 1.5, policePerKm = 0.0, safehousePerKm = 0.0,
        tags = listOf(RouteTag("최단 거리", "outline")),
    ),
    ;

    companion object {
        /** 요청 routeType(문자열/지정 id) → 유형. 미상/누락이면 SAFE(안전 우선 기본). */
        fun from(value: String?): RouteType {
            val v = value?.trim()?.lowercase() ?: return SAFE
            return entries.firstOrNull { it.apiType == v || it.id == v } ?: SAFE
        }
    }
}
