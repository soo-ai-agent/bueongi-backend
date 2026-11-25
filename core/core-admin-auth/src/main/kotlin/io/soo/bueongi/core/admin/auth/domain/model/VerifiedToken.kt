package io.soo.bueongi.core.admin.auth.domain.model

data class VerifiedToken(
    val userId: Long,
    val roles: List<String>,
)
