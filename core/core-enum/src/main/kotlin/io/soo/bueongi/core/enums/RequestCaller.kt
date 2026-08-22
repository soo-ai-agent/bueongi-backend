package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

/**
 * 요청을 보낸 쪽. 관리자 화면과 사용자 앱은 트래픽 성격이 달라 따로 세고 따로 보여 준다.
 * [wireValue] 가 와이어 계약이다.
 */
enum class RequestCaller(@get:JsonValue val wireValue: String) {
    ADMIN("admin"),
    USER("user"),
}
