package io.soo.bueongi.core.admin.domain.routestats

import io.soo.bueongi.core.enums.SafetyPreference

data class PreferenceCount(
    val preference: SafetyPreference,
    val count: Int,
)
