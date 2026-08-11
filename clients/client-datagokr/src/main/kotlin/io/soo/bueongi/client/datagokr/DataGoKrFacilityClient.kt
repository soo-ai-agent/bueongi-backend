package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/** data.go.kr 인증키는 Encoding(URL-인코딩) 형태 그대로 사용 — 재인코딩하면 키가 거부돼 URI 를 문자열로 조립한다. */
@Component
class DataGoKrFacilityClient(
    @Value("\${bueongi.datagokr.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(DataGoKrFacilityClient::class.java)
    private val serviceKey: String = serviceKey.trim().removeSurrounding("'").removeSurrounding("\"")

    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    fun fetchCctv(): List<DataGoKrPoint>? = fetchAll(CCTV_ENDPOINT, CCTV_FIELDS)

    fun fetchEmergencyBells(): List<DataGoKrPoint>? = fetchAll(BELL_ENDPOINT, BELL_FIELDS)

    fun fetchSafehouses(): List<DataGoKrPoint>? = fetchAll(SAFEHOUSE_ENDPOINT, SAFEHOUSE_FIELDS)

    fun fetchSecurityLamps(): List<DataGoKrPoint>? = fetchAll(SECURITY_LAMP_ENDPOINT, SECURITY_LAMP_FIELDS)

    private fun fetchAll(endpoint: String, fields: FieldNames): List<DataGoKrPoint>? {
        check(isEnabled) { "data.go.kr 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points: ArrayList<DataGoKrPoint> = ArrayList<DataGoKrPoint>()
        var pageNo: Int = 1
        var received: Long = 0L
        var totalCount: Long = Long.MAX_VALUE

        while (pageNo <= MAX_PAGES && received < totalCount) {
            val envelope: DataGoKrEnvelope? = try {
                restClient.get()
                    .uri(URI.create("$endpoint?serviceKey=$serviceKey&pageNo=$pageNo&numOfRows=$PAGE_SIZE&type=json"))
                    .retrieve()
                    .body(DataGoKrEnvelope::class.java)
            } catch (e: Exception) {
                // 예외 메시지에 URL(serviceKey 포함)이 섞일 수 있어 마스킹
                log.warn(
                    "data.go.kr 호출 실패(endpoint={}, page={}): {} — 갱신 중단(기존 데이터 유지)",
                    endpoint, pageNo, sanitize(e.message),
                )
                return null
            }
            if (envelope == null) {
                log.warn("data.go.kr 빈 응답(endpoint={}, page={}) — 갱신 중단(기존 데이터 유지)", endpoint, pageNo)
                return null
            }
            val response: DataGoKrResponse = envelope.payload()
            val header: DataGoKrHeader? = response.header
            val code: String? = header?.resultCode
            if (!code.isNullOrBlank() && code !in SUCCESS_CODES) {
                log.warn(
                    "data.go.kr resultCode={}({}) (endpoint={}) — 갱신 중단(기존 데이터 유지)",
                    code, header.resultMsg ?: "", endpoint,
                )
                return null
            }

            val rows: List<JsonNode> = extractItems(response.body?.items)
            response.body?.totalCount?.let { totalCount = it }
            received += rows.size
            rows.mapNotNullTo(points) { it.toPoint(fields) }

            if (rows.isEmpty() || rows.size < PAGE_SIZE) {
                if (totalCount == Long.MAX_VALUE) {
                    totalCount = received
                }
                break
            }
            pageNo += 1
        }
        if (received < totalCount) {
            log.warn(
                "data.go.kr 페이지 상한 도달(endpoint={}, received={}, total={}) — 갱신 중단(기존 데이터 유지)",
                endpoint, received, totalCount,
            )
            return null
        }
        return points
    }

    private fun extractItems(items: JsonNode?): List<JsonNode> = when {
        items == null -> emptyList()
        items.isArray -> items.toList()
        items.isObject && items.has("item") -> {
            val inner: JsonNode = items.get("item")
            when {
                inner.isArray -> inner.toList()
                inner.isObject -> listOf(inner)
                else -> emptyList()
            }
        }
        else -> emptyList()
    }

    private fun sanitize(message: String?): String =
        (message ?: "원인 미상").replace(serviceKey, "***")

    companion object {
        const val CCTV_ENDPOINT: String = "https://apis.data.go.kr/1741000/cctv_info/info"
        const val BELL_ENDPOINT: String = "https://apis.data.go.kr/1741000/emergency_call_box_info/info"
        const val SAFEHOUSE_ENDPOINT: String = "https://api.data.go.kr/openapi/tn_pubr_public_female_safety_prtchouse_api"
        const val SECURITY_LAMP_ENDPOINT: String = "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api"

        /** 표준 조회서비스 페이지당 최대 건수. */
        const val PAGE_SIZE: Int = 100

        /** 전국 CCTV 약 38만 행 전량 수집을 감당하는 상한. */
        const val MAX_PAGES: Int = 5000
        private val SUCCESS_CODES: Set<String> = setOf("0", "00")

        private val CCTV_FIELDS: FieldNames = FieldNames(
            lat = listOf("WGS84_LAT", "latitude"),
            lng = listOf("WGS84_LOT", "longitude"),
            name = listOf("MNG_INST_NM", "institutionNm"),
            address = listOf("LCTN_ROAD_NM_ADDR", "LCTN_LOTNO_ADDR", "rdnmadr"),
            sigungu = emptyList(),
        )
        private val BELL_FIELDS: FieldNames = FieldNames(
            lat = listOf("WGS84_LAT", "lat"),
            lng = listOf("WGS84_LOT", "lon"),
            name = listOf("INSTL_PSTN", "MNG_INST_NM", "mng_inst"),
            address = listOf("LCTN_ROAD_NM_ADDR", "LCTN_LOTNO_ADDR", "rn_adres"),
            sigungu = listOf("sgg_cd"),
        )

        /** 표준데이터 영문 공통 규격(latitude/longitude/rdnmadr) 1순위 + 한글 컬럼 응답 폴백. */
        private val SAFEHOUSE_FIELDS: FieldNames = FieldNames(
            lat = listOf("latitude", "위도"), lng = listOf("longitude", "경도"),
            name = listOf("storNm", "institutionNm", "업소명", "점포명"),
            address = listOf("rdnmadr", "lnmadr", "소재지도로명주소"), sigungu = emptyList(),
        )

        /** 보안등도 같은 표준데이터 규격 — 영문 1순위 + 한글 폴백(안심지킴이집과 동일 전략). */
        private val SECURITY_LAMP_FIELDS: FieldNames = FieldNames(
            lat = listOf("latitude", "위도"), lng = listOf("longitude", "경도"),
            name = listOf("scrtyLmpLctnNm", "institutionNm", "보안등위치명", "관리번호"),
            address = listOf("rdnmadr", "lnmadr", "소재지도로명주소", "소재지지번주소"),
            sigungu = listOf("sggCd", "시군구코드"),
        )
    }
}



private fun JsonNode.toPoint(fields: FieldNames): DataGoKrPoint? {
    val lat: Double = text(fields.lat)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val lng: Double = text(fields.lng)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    return DataGoKrPoint(
        lat = lat,
        lng = lng,
        name = text(fields.name),
        address = text(fields.address),
        sigungu = text(fields.sigungu),
    )
}

private fun JsonNode.text(candidates: List<String>): String? =
    candidates.firstNotNullOfOrNull { field ->
        get(field)?.asText()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    }
