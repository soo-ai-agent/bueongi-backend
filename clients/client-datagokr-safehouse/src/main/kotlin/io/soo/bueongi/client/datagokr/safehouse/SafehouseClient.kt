package io.soo.bueongi.client.datagokr.safehouse

import io.soo.bueongi.client.datagokr.DataGoKrFieldNames
import io.soo.bueongi.client.datagokr.DataGoKrPagedFetcher
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.DataGoKrQuery
import io.soo.bueongi.core.enums.DataGoKrQueryDialect
import org.springframework.stereotype.Component

/** 여성안심지킴이집(표준데이터). */
@Component
class SafehouseClient(
    private val fetcher: DataGoKrPagedFetcher,
) {
    val isEnabled: Boolean
        get() = fetcher.isEnabled

    /** 계약: null = 수집 실패(호출자는 기존 데이터 유지), 빈 목록과 다르다. 조건 없이 부르면 전량이라 쿼터를 크게 먹는다. */
    fun fetch(query: DataGoKrQuery = DataGoKrQuery()): List<DataGoKrPoint>? =
        fetcher.fetchAll(ENDPOINT, FIELDS, query, DataGoKrQueryDialect.FIELD)

    private companion object {
        const val ENDPOINT: String = "https://api.data.go.kr/openapi/tn_pubr_public_female_safety_prtchouse_api"

        val FIELDS: DataGoKrFieldNames = DataGoKrFieldNames(
            lat = listOf("latitude", "위도"),
            lng = listOf("longitude", "경도"),
            // institutionNm 은 관리 부서라 이름으로 쓰지 않는다(보안등에서 겪은 것과 같은 함정).
            name = listOf("storNm", "업소명", "점포명"),
            address = listOf("rdnmadr", "lnmadr", "소재지도로명주소"),
            sigungu = emptyList(),
        )
    }
}
