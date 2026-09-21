package io.soo.bueongi.client.safemap

data class SafemapPolicePoint(
    val lat: Double,
    val lng: Double,
    // 원천이 시설명을 비워 보내는 행이 있어 null 이 가능함
    val name: String? = null,
    // 원천이 주소를 비워 보내는 행이 있어 null 이 가능함
    val address: String? = null,
    // 무인 치안센터처럼 전화가 없는 시설이 있어 null 이 가능함
    val phone: String? = null,
    // 원천이 시군구 코드를 생략하는 행이 있어 null 이 가능함
    val sigungu: String? = null,
)
