package io.soo.bueongi.client.datagokr.securitylamp

import io.soo.bueongi.client.datagokr.DataGoKrPagedFetcher
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.query.DataGoKrFieldNames
import org.springframework.stereotype.Component

// 전국 보안등(표준데이터): https://www.data.go.kr/data/15149415/openapi.do
@Component
class SecurityLampClient(
    private val fetcher: DataGoKrPagedFetcher,
) {
    val isEnabled: Boolean
        get() = fetcher.isEnabled

    fun fetchSecurityLamps(municipalityCode: String? = null): List<DataGoKrPoint>? = fetcher.fetchAll(ENDPOINT, FIELDS, municipalityCode)

    private companion object {
        const val ENDPOINT: String = "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api"

        val FIELDS: DataGoKrFieldNames =
            DataGoKrFieldNames(
                lat = listOf("latitude", "위도"),
                lng = listOf("longitude", "경도"),
                name = listOf("lmpLcNm", "보안등위치명", "관리번호"),
                address = listOf("rdnmadr", "lnmadr", "소재지도로명주소", "소재지지번주소"),
                sigungu = listOf("insttCode", "sggCd", "시군구코드", "제공기관코드"),
                phone = listOf("phoneNumber", "관리기관전화번호"),
            )
    }
}
