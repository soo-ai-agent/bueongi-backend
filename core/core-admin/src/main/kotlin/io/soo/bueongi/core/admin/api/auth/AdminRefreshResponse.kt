package io.soo.bueongi.core.admin.api.auth

import io.soo.bueongi.core.admin.api.auth.AdminTokenBundle

data class AdminRefreshResponse(
    val token: AdminTokenBundle,
)
