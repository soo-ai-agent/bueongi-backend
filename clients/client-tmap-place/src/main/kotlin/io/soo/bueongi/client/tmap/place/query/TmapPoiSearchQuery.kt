package io.soo.bueongi.client.tmap.place.query

import org.springframework.web.util.UriBuilder
import java.net.URI

private const val SEARCH_COUNT: Int = 15

internal fun buildPoiSearchUri(
    builder: UriBuilder,
    keyword: String,
): URI =
    builder.queryParam("version", 1)
        .queryParam("searchKeyword", keyword)
        .queryParam("count", SEARCH_COUNT)
        .queryParam("reqCoordType", "WGS84GEO")
        .queryParam("resCoordType", "WGS84GEO")
        .build()
