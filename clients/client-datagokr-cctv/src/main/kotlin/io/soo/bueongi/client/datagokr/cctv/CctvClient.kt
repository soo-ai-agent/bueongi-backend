package io.soo.bueongi.client.datagokr.cctv

import io.soo.bueongi.client.datagokr.DataGoKrFieldNames
import io.soo.bueongi.client.datagokr.DataGoKrPagedFetcher
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import org.springframework.stereotype.Component

/** 전국 CCTV(행안부 조회서비스). 전송(페이징·재시도)은 [DataGoKrPagedFetcher] 가 맡고 여기는 규격만 갖는다. */
@Component
class CctvClient(
    private val fetcher: DataGoKrPagedFetcher,
) {
    val isEnabled: Boolean
        get() = fetcher.isEnabled

    /** 계약: null = 수집 실패 — 호출자는 기존 데이터를 유지한다. 빈 목록과 다르다. */
    fun fetch(): List<DataGoKrPoint>? =
        fetcher.fetchAll(ENDPOINT, FIELDS)

    private companion object {
        const val ENDPOINT: String = "https://apis.data.go.kr/1741000/cctv_info/info"

        val FIELDS: DataGoKrFieldNames = DataGoKrFieldNames(
        lat = listOf("WGS84_LAT", "latitude"),
        lng = listOf("WGS84_LOT", "longitude"),
        name = listOf("MNG_INST_NM", "institutionNm"),
        address = listOf("LCTN_ROAD_NM_ADDR", "LCTN_LOTNO_ADDR", "rdnmadr"),
        sigungu = emptyList(),
        )
    }
}
