package io.soo.bueongi.core.route.stats.domain.model

data class RouteChoiceSummary(
    val total: Int,
    val windowCap: Int,
    val byType: List<RouteTypeCount>,
    val byPreference: List<PreferenceCount>,
)
