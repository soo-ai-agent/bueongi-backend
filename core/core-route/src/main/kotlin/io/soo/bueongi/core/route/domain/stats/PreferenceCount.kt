package io.soo.bueongi.core.route.domain.stats

import io.soo.bueongi.core.enums.SafetyPreference

data class PreferenceCount(
    val preference: SafetyPreference,
    val count: Int,
)
