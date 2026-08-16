package io.soo.bueongi.storage.db.core.safetyspot.model

data class SafePath(
    val coords: List<GeoPoint>,
    // 원천에 이름이 없는 구간이 있어 null 이 가능함
    val name: String? = null,
)
