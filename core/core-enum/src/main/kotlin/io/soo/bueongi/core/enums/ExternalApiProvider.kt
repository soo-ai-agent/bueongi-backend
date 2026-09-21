package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

enum class ExternalApiProvider(
    @get:JsonValue val wireValue: String,
) {
    TMAP("tmap"),
    KAKAO("kakao"),
    DATA_GO_KR("data_go_kr"),
    SAFETYDATA("safetydata"),
    SAFEMAP("safemap"),
    SAFE182("safe182"),
    SLACK("slack"),

    UNKNOWN("unknown"),
}
