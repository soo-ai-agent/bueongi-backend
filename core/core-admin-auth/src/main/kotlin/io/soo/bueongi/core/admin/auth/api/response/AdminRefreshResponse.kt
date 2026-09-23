package io.soo.bueongi.core.admin.auth.api.response

import io.soo.bueongi.core.admin.auth.domain.model.AdminTokenBundle

data class AdminRefreshResponse(
    val token: AdminTokenBundle,
)
