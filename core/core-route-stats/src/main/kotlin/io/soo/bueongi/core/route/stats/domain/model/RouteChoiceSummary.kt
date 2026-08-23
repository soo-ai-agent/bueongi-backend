package io.soo.bueongi.core.route.stats.domain.model

/** [windowCap] 은 선택 창의 상한 — 추천 창과 다른 값이라는 것이 응답에서 보여야 한다. */
data class RouteChoiceSummary(
    val total: Int,
    val windowCap: Int,
    val byType: List<RouteTypeCount>,
    val byPreference: List<PreferenceCount>,
)
