package io.soo.bueongi.core.domain.admin

data class AdminIdentity(
    val userId: Long,
    val roles: List<String>,
)
