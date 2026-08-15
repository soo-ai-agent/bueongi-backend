package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

/** [apiType] 이 와이어 계약([JsonValue]) — RouteOption.type 이 정확히 'safe'|'main'|'fast' 를 내야 하는 프론트 계약. */
enum class RouteType(@get:JsonValue val apiType: String) {
    SAFE("safe"),
    MAIN("main"),
    FAST("fast"),
}
