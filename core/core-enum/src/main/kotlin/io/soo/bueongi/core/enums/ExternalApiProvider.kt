package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

/**
 * 우리가 부르는 외부 API 원천. [wireValue] 가 와이어 계약이다.
 *
 * 호스트에서 이 값을 알아내는 일은 여기 두지 않는다 — enum 은 값만 갖는다.
 */
enum class ExternalApiProvider(@get:JsonValue val wireValue: String) {
    TMAP("tmap"),
    KAKAO("kakao"),
    DATA_GO_KR("data_go_kr"),
    SAFETYDATA("safetydata"),
    SAFEMAP("safemap"),
    SAFE182("safe182"),
    SLACK("slack"),

    /** 아직 분류하지 않은 호스트 — 새 클라이언트를 붙이고 여기 등록을 잊으면 이리로 모인다. */
    UNKNOWN("unknown"),
}
