package io.soo.bueongi.client.datagokr.query

data class DataGoKrFieldNames(
    val lat: List<String>,
    val lng: List<String>,
    val name: List<String>,
    val address: List<String>,
    val sigungu: List<String>,
    val phone: List<String> = emptyList(),
)
