package io.soo.bueongi.client.datagokr

/** 원천마다 다른 컬럼명을 논리 필드로 잇는 후보 목록 — 앞에서부터 먼저 잡히는 것을 쓴다. */
data class DataGoKrFieldNames(
    val lat: List<String>,
    val lng: List<String>,
    val name: List<String>,
    val address: List<String>,
    val sigungu: List<String>,
    val phone: List<String> = emptyList(),
)
