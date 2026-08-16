package io.soo.bueongi.core.route.domain.model

/** 기준일은 manifest 가 있는 원천만 안다. */
data class DataProvenance(
    val kind: String,
    val basedOn: String? = null,
    val origin: String,
)
