package io.soo.bueongi.client.datagokr

/**
 * 좌표 없이 주소만 온 행. 지자체가 좌표를 안 올린 데이터(서울 보안등 등)를
 * 지오코딩으로 살리려면 버리지 말고 여기로 남겨야 한다.
 */
data class DataGoKrAddressRow(
    val address: String,
    // 원천이 이름을 비워 보내는 행이 있어 null 이 가능함
    val name: String? = null,
    // 시군구 컬럼 자체가 없는 원천이 있어 null 이 가능함
    val sigungu: String? = null,
    // 관리기관 전화를 주지 않는 종류가 있어 null 이 가능함
    val phone: String? = null,
)
