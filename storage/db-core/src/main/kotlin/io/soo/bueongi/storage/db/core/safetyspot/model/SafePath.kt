package io.soo.bueongi.storage.db.core.safetyspot.model

/** 안심귀갓길 이름은 원천에 없는 구간이 있다. */
data class SafePath(
    val coords: List<GeoPoint>,
    val name: String? = null,
)
