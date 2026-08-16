package io.soo.bueongi.core.share.domain.route.model

/** 보호자 화면은 경로가 저장되기 전에도 열린다. */
data class RouteView(
    val expired: Boolean,
    val route: RouteContext?,
) {
    companion object {

        val EXPIRED = RouteView(expired = true, route = null)
    }
}
