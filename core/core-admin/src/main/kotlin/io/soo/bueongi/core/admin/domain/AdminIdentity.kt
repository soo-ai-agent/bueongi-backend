package io.soo.bueongi.core.admin.domain

data class AdminIdentity(
    val userId: Long,
    val roles: List<String>,
)
