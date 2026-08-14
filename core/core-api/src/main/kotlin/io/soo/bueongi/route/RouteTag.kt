package io.soo.bueongi.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.GeoPoint

data class RouteTag(
    val text: String,
    val variant: RouteTagVariant,
)
