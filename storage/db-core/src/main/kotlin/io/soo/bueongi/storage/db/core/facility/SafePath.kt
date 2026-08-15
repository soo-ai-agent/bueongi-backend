package io.soo.bueongi.storage.db.core.facility

data class SafePath(
    val coords: List<GeoPoint>,
    val name: String? = null,
)
