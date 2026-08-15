package io.soo.bueongi.core.domain.admin.routestats

import io.soo.bueongi.core.enums.SafetyPreference

data class PreferenceCount(
    val preference: SafetyPreference,
    val count: Int,
)
