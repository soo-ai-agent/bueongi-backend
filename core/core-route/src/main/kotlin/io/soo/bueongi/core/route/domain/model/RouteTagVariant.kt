package io.soo.bueongi.core.route.domain.model

import com.fasterxml.jackson.annotation.JsonValue

// wire 값은 프론트 Badge variant 와 글자 그대로 맞춰야 하는 와이어 계약이다.
enum class RouteTagVariant(
    @get:JsonValue val wire: String,
) {
    DEFAULT("default"),
    MINT("mint"),
    BLUE("blue"),
    YELLOW("yellow"),
    OUTLINE("outline"),
}
