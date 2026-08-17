package io.soo.bueongi.client.datagokr.lamp

import io.soo.bueongi.client.datagokr.DataGoKrFieldNames
import io.soo.bueongi.client.datagokr.DataGoKrPagedFetcher
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.DataGoKrQuery
import io.soo.bueongi.core.enums.DataGoKrQueryDialect
import org.springframework.stereotype.Component

/** 전국 보안등(표준데이터) — 안심지킴이집과 같은 영문 1순위 + 한글 폴백 전략. 전송(페이징·재시도)은 [DataGoKrPagedFetcher] 가 맡고 여기는 규격만 갖는다. */
@Component
class SecurityLampClient(
    private val fetcher: DataGoKrPagedFetcher,
) {
    val isEnabled: Boolean
        get() = fetcher.isEnabled

    /**
     * 계약: null = 수집 실패 — 호출자는 기존 데이터를 유지한다. 빈 목록과 다르다.
     * 조건 없이 부르면 전량 수집이라 쿼터를 크게 먹는다 — 자치단체·갱신시점으로 좁혀 부른다.
     */
    fun fetch(query: DataGoKrQuery = DataGoKrQuery()): List<DataGoKrPoint>? =
        fetcher.fetchAll(ENDPOINT, FIELDS, query, DataGoKrQueryDialect.FIELD)

    private companion object {
        const val ENDPOINT: String = "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api"

        val FIELDS: DataGoKrFieldNames = DataGoKrFieldNames(
        lat = listOf("latitude", "위도"),
        lng = listOf("longitude", "경도"),
        name = listOf("scrtyLmpLctnNm", "institutionNm", "보안등위치명", "관리번호"),
        address = listOf("rdnmadr", "lnmadr", "소재지도로명주소", "소재지지번주소"),
        sigungu = listOf("sggCd", "시군구코드"),
        )
    }
}
