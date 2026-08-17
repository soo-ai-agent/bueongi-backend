package io.soo.bueongi.client.safetydata.streetlight

data class StreetlightPoint(
    val lat: Double,
    val lng: Double,
    // 원천이 주소를 비워 보내는 행이 많음
    val address: String? = null,
    // 원천이 시군구 코드를 비워 보내는 행이 많음
    val sigungu: String? = null,
)
