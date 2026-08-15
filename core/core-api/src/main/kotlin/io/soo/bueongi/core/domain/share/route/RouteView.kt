package io.soo.bueongi.core.domain.share.route

data class RouteView(
    val expired: Boolean,
    val route: RouteContext?,
) {
    companion object {

        val EXPIRED: RouteView = RouteView(expired = true, route = null)
    }
}
