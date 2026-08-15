package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.enums.RouteTag
import io.soo.bueongi.core.enums.RouteTagVariant
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.domain.model.RouteTypeDisplay
import org.springframework.stereotype.Component

/** 경로 종류별 표시 값을 한곳에서 답한다. 문구·계수를 고칠 일이 생기면 이 파일만 본다. */
@Component
class RouteTypeCatalog {

    fun displayOf(type: RouteType): RouteTypeDisplay = DISPLAYS.getValue(type)

    /**
     * 요청이 보낸 문자열을 종류로 읽는다. apiType("safe")과 id("1") 둘 다 받는다.
     * 모르는 값·빈 값은 거절하지 않고 가장 안전한 SAFE 로 읽는다 —
     * 안심 앱에서 종류를 못 알아들었다고 길 안내를 막는 것보다 안전한 길을 주는 편이 낫다.
     */
    fun typeOf(value: String?): RouteType {
        val normalized: String = normalize(value)
        for (type in RouteType.entries) {
            if (type.apiType == normalized || displayOf(type).id == normalized) {
                return type
            }
        }
        return RouteType.SAFE
    }

    private fun normalize(value: String?): String {
        if (value == null) {
            return ""
        }
        return value.trim().lowercase()
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
