package io.soo.bueongi.core.common.event

// 공통 advice 가 ApiException 오류 응답을 내보낼 때마다 발행한다. 어떤 code 를 운영자에게 알릴지는 admin-ops 리스너가 정한다.
data class ApiErrorResponded(
    val code: String,
    // 외부 연동 실패 알림에 원인 예외 이름을 싣는다. 원인이 없는 예외는 null.
    val causeName: String?,
)
