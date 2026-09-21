package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.model.RouteTagVariant
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.domain.model.RouteTypeDisplay
import org.springframework.stereotype.Component

@Component
class RouteTypeCatalog {

    fun displayOf(type: RouteType): RouteTypeDisplay =
        DISPLAYS.getValue(type)

    fun typeOf(value: String?): RouteType {
        val normalized: String = value?.trim()?.lowercase().orEmpty()
        for (type in RouteType.entries) {
            if (type.apiType == normalized || displayOf(type).id == normalized) {
                return type
            }
        }
        return RouteType.SAFE
    }


    private companion object {

        val DISPLAYS: Map<RouteType, RouteTypeDisplay> = mapOf(
            RouteType.SAFE to RouteTypeDisplay(
                id = "1",
                name = "추천 경로",
                scoredName = "안심 경로",
                desc = "가장 밝고 안심 시설이 잘 갖춰진 길입니다.",
                distanceFactor = 1.18,
                tags = listOf(RouteTag("CCTV 많음", RouteTagVariant.MINT), RouteTag("밝은 길", RouteTagVariant.YELLOW)),
                signatureTags = listOf(RouteTag("안심", RouteTagVariant.MINT)),
            ),
            RouteType.MAIN to RouteTypeDisplay(
                id = "2",
                name = "주요 도로",
                scoredName = "기본 경로",
                desc = "사람이 많이 다니는 주요 도로 위주의 길입니다.",
                distanceFactor = 1.05,
                tags = listOf(RouteTag("주요 도로", RouteTagVariant.BLUE), RouteTag("유동인구 많음", RouteTagVariant.DEFAULT)),
                signatureTags = emptyList(),
            ),
            RouteType.FAST to RouteTypeDisplay(
                id = "3",
                name = "최단 경로",
                scoredName = "빠른 경로",
                desc = "거리·시간이 가장 짧은 길입니다.",
                distanceFactor = 0.92,
                tags = listOf(RouteTag("최단 거리", RouteTagVariant.OUTLINE)),
                signatureTags = listOf(RouteTag("빠른길", RouteTagVariant.BLUE)),
            ),
        )
    }
}
