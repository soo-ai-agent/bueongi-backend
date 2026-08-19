package io.soo.bueongi.client.datagokr

/** 수집 결과 — 좌표가 있는 지점과, 좌표 없이 주소만 있어 지오코딩이 필요한 행을 나눠 담는다. */
data class DataGoKrFetchResult(
    val points: List<DataGoKrPoint>,
    val addressOnlyRows: List<DataGoKrAddressRow>,
)
