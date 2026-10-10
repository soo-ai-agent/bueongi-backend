package io.soo.bueongi.core.route.api.response

// 받는 쪽이 basedOn 에 값이 있는지로만 보고 기준일 문구를 접어서, 기준일(YYYY.MM)을 모르면 빈 문자열로 둔다.
data class DataProvenance(
    val kind: String,
    val basedOn: String = "",
    val origin: String,
)
