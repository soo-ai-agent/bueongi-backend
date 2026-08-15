package io.soo.bueongi.core.route.domain.model

/**
 * 경로 종류를 화면에 어떻게 보여줄지. enum 은 종류만 세고, 이 표시 값은 [RouteTypeCatalog] 가 갖는다.
 *
 * [id] 는 프론트가 목록 키로 쓰고 요청에 그대로 되돌려 보내기도 한다("1"|"2"|"3").
 * [distanceFactor] 는 Tmap 없이 안내할 때 직선거리에 곱하는 우회 계수다.
 */
data class RouteTypeDisplay(
    val id: String,
    val name: String,
    val scoredName: String,
    val desc: String,
    val distanceFactor: Double,
    val tags: List<RouteTag>,
    val signatureTags: List<RouteTag>,
)
