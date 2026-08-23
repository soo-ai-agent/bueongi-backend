package io.soo.bueongi.core.route.stats.domain.model

import io.soo.bueongi.core.enums.RouteType

data class RouteTypeCount(
    val type: RouteType,
    val count: Int,
)
