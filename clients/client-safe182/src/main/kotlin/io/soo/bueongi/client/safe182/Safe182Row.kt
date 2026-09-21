package io.soo.bueongi.client.safe182

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 좌표가 WGS84 경위도로 그대로 온다 — 변환이 필요 없다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Safe182Row(
    // 원천이 좌표를 비워 보내는 행이 있어 null 이 가능함
    val lcinfoLa: Double? = null,
    // 원천이 좌표를 비워 보내는 행이 있어 null 이 가능함
    val lcinfoLo: Double? = null,
    // 원천이 시설명을 비워 보내는 행이 있어 null 이 가능함
    val bsshNm: String? = null,
    // 원천이 주소를 비워 보내는 행이 있어 null 이 가능함
    val adres: String? = null,
    // 전화가 없는 시설이 있어 null 이 가능함
    val telno: String? = null,
)
