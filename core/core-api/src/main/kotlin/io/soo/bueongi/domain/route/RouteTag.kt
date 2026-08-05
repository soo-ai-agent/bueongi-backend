package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.GeoPoint

/**
 * 경로 표시 태그. 프론트 RouteTag 와 호환되도록 text/variant 만 보낸다 —
 * icon(ReactNode)은 직렬화 불가이므로 프론트가 variant→아이콘으로 매핑한다.
 */
data class RouteTag(
    val text: String,
    val variant: RouteTagVariant,
)
