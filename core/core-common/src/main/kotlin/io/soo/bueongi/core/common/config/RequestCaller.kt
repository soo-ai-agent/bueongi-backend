package io.soo.bueongi.core.common.config

import com.fasterxml.jackson.annotation.JsonValue

enum class RequestCaller(
    @get:JsonValue val wireValue: String,
) {
    ADMIN("admin"),
    USER("user"),
}
