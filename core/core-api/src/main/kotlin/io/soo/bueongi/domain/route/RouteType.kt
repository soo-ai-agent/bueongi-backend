package io.soo.bueongi.domain.route

/**
 * 경로 유형. 직선거리(haversine)에 유형별 우회/단축 계수를 곱해 시간·거리를 만든다. 결정론적.
 *
 * 안심 귀가 도메인: safe(가장 밝고 안전) → main(주요 도로) → fast(최단)로 갈수록 우회가 준다.
 * (과거의 유형별 시설 밀도(perKm) 파라미터는 밀도 추정 폐기로 제거됐다 — 시설 개수는 회랑 실측만 쓴다.)
 */
enum class RouteType(
    val id: String,
    val apiType: String,
    val displayName: String,
    val desc: String,
    /** 직선거리 대비 실제 보행거리 계수(우회). */
    val distanceFactor: Double,
    val tags: List<RouteTag>,
) {
    SAFE(
        id = "1",
        apiType = "safe",
        displayName = "추천 경로",
        desc = "가장 밝고 안심 시설이 잘 갖춰진 길입니다.",
        distanceFactor = 1.18,
        tags = listOf(RouteTag("CCTV 많음", "mint"), RouteTag("밝은 길", "yellow")),
    ),
    MAIN(
        id = "2",
        apiType = "main",
        displayName = "주요 도로",
        desc = "사람이 많이 다니는 주요 도로 위주의 길입니다.",
        distanceFactor = 1.05,
        tags = listOf(RouteTag("주요 도로", "blue"), RouteTag("유동인구 많음", "default")),
    ),
    FAST(
        id = "3",
        apiType = "fast",
        displayName = "최단 경로",
        desc = "거리·시간이 가장 짧은 길입니다.",
        distanceFactor = 0.92,
        tags = listOf(RouteTag("최단 거리", "outline")),
    ),
    ;

    companion object {
        /** 요청 routeType(문자열/지정 id) → 유형. 미상/누락이면 SAFE(안전 우선 기본). */
        fun from(value: String?): RouteType {
            val normalized = value?.trim()?.lowercase() ?: return SAFE
            return entries.firstOrNull { it.apiType == normalized || it.id == normalized } ?: SAFE
        }
    }
}
