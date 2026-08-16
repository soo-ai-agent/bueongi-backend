package io.soo.bueongi.core.route.domain.model

data class DataProvenance(
    val kind: String,
    // manifest 가 없는 원천은 null 이 가능함
    val basedOn: String? = null,
    val origin: String,
)
