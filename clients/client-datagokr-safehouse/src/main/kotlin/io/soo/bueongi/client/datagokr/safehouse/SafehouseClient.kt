package io.soo.bueongi.client.datagokr.safehouse

import io.soo.bueongi.client.datagokr.DataGoKrFieldNames
import io.soo.bueongi.client.datagokr.DataGoKrPagedFetcher
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import org.springframework.stereotype.Component

/** 여성안심지킴이집(표준데이터) — 영문 공통 규격 1순위 + 한글 컬럼 폴백. 전송(페이징·재시도)은 [DataGoKrPagedFetcher] 가 맡고 여기는 규격만 갖는다. */
@Component
class SafehouseClient(
    private val fetcher: DataGoKrPagedFetcher,
) {
    val isEnabled: Boolean
        get() = fetcher.isEnabled

    /** 계약: null = 수집 실패 — 호출자는 기존 데이터를 유지한다. 빈 목록과 다르다. */
    fun fetch(): List<DataGoKrPoint>? =
        fetcher.fetchAll(ENDPOINT, FIELDS)

    private companion object {
        const val ENDPOINT: String = "https://api.data.go.kr/openapi/tn_pubr_public_female_safety_prtchouse_api"

        val FIELDS: DataGoKrFieldNames = DataGoKrFieldNames(
        lat = listOf("latitude", "위도"),
        lng = listOf("longitude", "경도"),
        name = listOf("storNm", "institutionNm", "업소명", "점포명"),
        address = listOf("rdnmadr", "lnmadr", "소재지도로명주소"),
        sigungu = emptyList(),
        )
    }
}
