package io.soo.bueongi.client.datagokr.bell

import io.soo.bueongi.client.datagokr.DataGoKrFieldNames
import io.soo.bueongi.client.datagokr.DataGoKrPagedFetcher
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.DataGoKrQuery
import org.springframework.stereotype.Component

// 전국 안전비상벨(행안부 조회서비스).
@Component
class EmergencyBellClient(
    private val fetcher: DataGoKrPagedFetcher,
) {
    val isEnabled: Boolean
        get() = fetcher.isEnabled

    // null = 수집 실패, 빈 목록과 다르다 — 호출자는 기존 데이터를 유지한다.
    fun fetch(query: DataGoKrQuery = DataGoKrQuery()): List<DataGoKrPoint>? = fetcher.fetchAll(ENDPOINT, FIELDS, query)

    private companion object {
        const val ENDPOINT: String = "https://apis.data.go.kr/1741000/emergency_call_box_info/info"

        val FIELDS: DataGoKrFieldNames =
            DataGoKrFieldNames(
                lat = listOf("WGS84_LAT", "lat"),
                lng = listOf("WGS84_LOT", "lon"),
                name = listOf("INSTL_PSTN", "MNG_INST_NM", "mng_inst"),
                address = listOf("LCTN_ROAD_NM_ADDR", "LCTN_LOTNO_ADDR", "rn_adres"),
                sigungu = listOf("sgg_cd"),
            )
    }
}
