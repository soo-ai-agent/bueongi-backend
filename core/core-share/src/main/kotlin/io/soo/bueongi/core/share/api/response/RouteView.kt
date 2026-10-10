package io.soo.bueongi.core.share.api.response

data class RouteView(
    val expired: Boolean,
    val route: RouteContext?,
) {
    companion object {
        val EXPIRED = RouteView(expired = true, route = null)
    }
}
