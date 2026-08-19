package io.soo.bueongi.client.datagokr.securitylamp

import io.soo.bueongi.client.datagokr.DataGoKrFetchResult
import io.soo.bueongi.client.datagokr.DataGoKrFieldNames
import io.soo.bueongi.client.datagokr.DataGoKrPagedFetcher
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.DataGoKrQuery
import io.soo.bueongi.core.enums.DataGoKrQueryDialect
import org.springframework.stereotype.Component

// https://www.data.go.kr/data/15149415/openapi.do
/** 전국 보안등(표준데이터). */
@Component
class SecurityLampClient(
    private val fetcher: DataGoKrPagedFetcher,
) {
    val isEnabled: Boolean
        get() = fetcher.isEnabled

    /** 계약: null = 수집 실패(호출자는 기존 데이터 유지), 빈 목록과 다르다. 조건 없이 부르면 전량이라 쿼터를 크게 먹는다. */
    fun fetch(query: DataGoKrQuery = DataGoKrQuery()): List<DataGoKrPoint>? =
        fetcher.fetchAll(ENDPOINT, FIELDS, query, DataGoKrQueryDialect.FIELD)

    /** [fetch] 와 같되, 좌표 없이 주소만 온 행(서울 자치구 다수)을 지오코딩용으로 함께 준다. */
    fun fetchWithAddressOnly(query: DataGoKrQuery = DataGoKrQuery()): DataGoKrFetchResult? =
        fetcher.fetchAllWithAddressOnly(ENDPOINT, FIELDS, query, DataGoKrQueryDialect.FIELD)

    private companion object {
        const val ENDPOINT: String = "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api"

        val FIELDS: DataGoKrFieldNames = DataGoKrFieldNames(
            lat = listOf("latitude", "위도"),
            lng = listOf("longitude", "경도"),
            name = listOf("lmpLcNm", "보안등위치명", "관리번호"),
            address = listOf("rdnmadr", "lnmadr", "소재지도로명주소", "소재지지번주소"),
            sigungu = listOf("insttCode", "sggCd", "시군구코드", "제공기관코드"),
            // 고장 신고에 쓸 수 있게 관리기관 전화를 함께 내려보낸다.
            phone = listOf("phoneNumber", "관리기관전화번호"),
        )
    }
}
