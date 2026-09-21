package io.soo.bueongi.client.datagokr

data class DataGoKrPoint(
    val lat: Double,
    val lng: Double,
    // 원천이 이름을 비워 보내는 행이 있어 null 이 가능함
    val name: String? = null,
    // 원천이 주소를 비워 보내는 행이 있어 null 이 가능함
    val address: String? = null,
    // 시군구 컬럼 자체가 없는 원천(CCTV)이 있어 null 이 가능함
    val sigungu: String? = null,
    // 관리기관 전화를 주지 않는 종류가 있어 null 이 가능함
    val phone: String? = null,
)
