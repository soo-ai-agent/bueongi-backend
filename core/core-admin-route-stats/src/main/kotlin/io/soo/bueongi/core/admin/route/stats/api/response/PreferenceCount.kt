package io.soo.bueongi.core.admin.route.stats.api.response

import io.soo.bueongi.core.enums.SafetyPreference

data class PreferenceCount(
    val preference: SafetyPreference,
    val count: Int,
)
