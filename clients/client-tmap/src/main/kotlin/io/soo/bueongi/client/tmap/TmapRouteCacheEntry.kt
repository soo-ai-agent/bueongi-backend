package io.soo.bueongi.client.tmap

internal data class TmapRouteCacheEntry(
    val route: TmapRoute,
    val expiresAt: Long,
)
