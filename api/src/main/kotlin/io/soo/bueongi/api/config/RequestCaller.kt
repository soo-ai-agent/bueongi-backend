package io.soo.bueongi.api.config

/** 요청을 보낸 쪽. 관리자 화면은 사용자 앱과 트래픽 성격이 달라 로그에서 갈라 읽는다. */
enum class RequestCaller(val label: String) {
    ADMIN("🛠 관리자"),
    USER("🙋 사용자"),
}
