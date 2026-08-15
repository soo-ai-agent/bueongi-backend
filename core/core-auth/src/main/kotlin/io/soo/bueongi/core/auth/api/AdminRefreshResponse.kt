package io.soo.bueongi.core.auth.api

import io.soo.bueongi.core.auth.domain.model.AdminTokenBundle

data class AdminRefreshResponse(
    val token: AdminTokenBundle,
)
