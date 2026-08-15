package io.soo.bueongi.storage.db.core.facility.model

data class SafePath(
    val coords: List<GeoPoint>,
    val name: String? = null,
)
