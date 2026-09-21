package io.soo.bueongi.client.safe182

data class ChildSafeHousePoint(
    val lat: Double,
    val lng: Double,
    // 원천이 시설명을 비워 보내는 행이 있어 null 이 가능함
    val name: String? = null,
    // 원천이 주소를 비워 보내는 행이 있어 null 이 가능함
    val address: String? = null,
    // 전화가 없는 시설이 있어 null 이 가능함
    val phone: String? = null,
)
