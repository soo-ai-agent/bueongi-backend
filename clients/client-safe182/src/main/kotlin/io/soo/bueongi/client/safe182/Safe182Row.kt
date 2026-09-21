package io.soo.bueongi.client.safe182

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 좌표가 WGS84 경위도로 그대로 온다 — 변환이 필요 없다.
// 원천이 어느 칸이든 비워 보내는 행이 있어 전부 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Safe182Row(
    val lcinfoLa: Double? = null,
    val lcinfoLo: Double? = null,
    val bsshNm: String? = null,
    val adres: String? = null,
    val telno: String? = null,
)
