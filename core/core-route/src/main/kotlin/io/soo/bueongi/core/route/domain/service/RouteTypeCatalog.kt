package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.model.RouteTagVariant
import io.soo.bueongi.core.route.domain.model.RouteTypeDisplay
import org.springframework.stereotype.Component

@Component
class RouteTypeCatalog {
    fun displayOf(type: RouteType): RouteTypeDisplay {
        return DISPLAYS.getValue(type)
    }

    private companion object {
        val DISPLAYS: Map<RouteType, RouteTypeDisplay> =
            mapOf(
                RouteType.SAFE to
                    RouteTypeDisplay(
                        scoredName = "안심 경로",
                        signatureTags = listOf(RouteTag("안심", RouteTagVariant.MINT)),
                    ),
                RouteType.MAIN to
                    RouteTypeDisplay(
                        scoredName = "기본 경로",
                        signatureTags = emptyList(),
                    ),
                RouteType.FAST to
                    RouteTypeDisplay(
                        scoredName = "빠른 경로",
                        signatureTags = listOf(RouteTag("빠른길", RouteTagVariant.BLUE)),
                    ),
            )
    }
}
