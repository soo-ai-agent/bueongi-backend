package io.soo.bueongi.core.share.api.response

import io.soo.bueongi.storage.db.share.model.StoredRoute

data class RouteView(
    val expired: Boolean,
    val route: RouteContext?,
) {
    companion object {
        val EXPIRED = RouteView(expired = true, route = null)

        // 공유는 살아 있지만 경로를 아직 올리지 않았으면 storedRoute 는 null.
        fun from(storedRoute: StoredRoute?): RouteView {
            var route: RouteContext? = null
            if (storedRoute != null) {
                route = RouteContext.from(storedRoute)
            }
            return RouteView(expired = false, route = route)
        }
    }
}
