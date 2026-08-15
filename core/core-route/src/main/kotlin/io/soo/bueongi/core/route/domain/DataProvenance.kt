package io.soo.bueongi.core.route.domain

data class DataProvenance(
    val kind: String,
    val basedOn: String? = null,
    val origin: String,
)
