package io.soo.bueongi.core.share.domain.route.model

data class RouteView(
    val expired: Boolean,
    // 경로를 저장하기 전에는 null 이 가능함
    val route: RouteContext?,
) {
    companion object {

        val EXPIRED = RouteView(expired = true, route = null)
    }
}
