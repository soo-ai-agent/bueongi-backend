package io.soo.bueongi.core.share.domain.route.model

data class RouteView(
    val expired: Boolean,
    val route: RouteContext?,
) {
    companion object {

        val EXPIRED = RouteView(expired = true, route = null)
    }
}
