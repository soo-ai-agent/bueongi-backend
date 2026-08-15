package io.soo.bueongi.core.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.GeoPoint

/** [wire] 가 와이어 계약([JsonValue]) — 정확히 'default'|'mint'|'blue'|'yellow'|'outline' 을 내야 하는 프론트 Badge variant. */
enum class RouteTagVariant(@get:JsonValue val wire: String) {
    DEFAULT("default"),
    MINT("mint"),
    BLUE("blue"),
    YELLOW("yellow"),
    OUTLINE("outline"),
    ;

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String): RouteTagVariant = entries.first { it.wire == value }
    }
}
