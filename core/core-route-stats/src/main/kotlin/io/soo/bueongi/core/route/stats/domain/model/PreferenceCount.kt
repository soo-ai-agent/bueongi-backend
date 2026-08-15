package io.soo.bueongi.core.route.stats.domain.model

import io.soo.bueongi.core.enums.SafetyPreference

data class PreferenceCount(
    val preference: SafetyPreference,
    val count: Int,
)
