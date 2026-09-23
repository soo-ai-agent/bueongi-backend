package io.soo.bueongi.core.route.domain.model

data class RouteTypeDisplay(
    val name: String,
    val scoredName: String,
    val desc: String,
    val tags: List<RouteTag>,
    val signatureTags: List<RouteTag>,
)
