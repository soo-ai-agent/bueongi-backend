package io.soo.bueongi.core.route.domain.model

// basedOn 은 기준일(YYYY.MM)이고, 알 수 없으면 빈 문자열이다 — 받는 쪽이 truthy 로 읽어 문구를 접는다.
data class DataProvenance(
    val kind: String,
    val basedOn: String = "",
    val origin: String,
)
