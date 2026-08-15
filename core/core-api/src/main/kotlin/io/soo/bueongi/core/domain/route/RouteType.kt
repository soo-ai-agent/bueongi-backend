package io.soo.bueongi.core.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** [apiType] 이 와이어 계약([JsonValue]) — RouteOption.type 이 정확히 'safe'|'main'|'fast' 를 내야 하는 프론트 계약. */
enum class RouteType(
    val id: String,
    @get:JsonValue val apiType: String,
    val displayName: String,
    val desc: String,
    val distanceFactor: Double,
    val tags: List<RouteTag>,
) {
    SAFE(
        id = "1",
        apiType = "safe",
        displayName = "추천 경로",
        desc = "가장 밝고 안심 시설이 잘 갖춰진 길입니다.",
        distanceFactor = 1.18,
        tags = listOf(RouteTag("CCTV 많음", RouteTagVariant.MINT), RouteTag("밝은 길", RouteTagVariant.YELLOW)),
    ),
    MAIN(
        id = "2",
        apiType = "main",
        displayName = "주요 도로",
        desc = "사람이 많이 다니는 주요 도로 위주의 길입니다.",
        distanceFactor = 1.05,
        tags = listOf(RouteTag("주요 도로", RouteTagVariant.BLUE), RouteTag("유동인구 많음", RouteTagVariant.DEFAULT)),
    ),
    FAST(
        id = "3",
        apiType = "fast",
        displayName = "최단 경로",
        desc = "거리·시간이 가장 짧은 길입니다.",
        distanceFactor = 0.92,
        tags = listOf(RouteTag("최단 거리", RouteTagVariant.OUTLINE)),
    ),
    ;

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String?): RouteType {
            val normalized: String = value?.trim()?.lowercase() ?: return SAFE
            return entries.firstOrNull { it.apiType == normalized || it.id == normalized } ?: SAFE
        }
    }
}
