package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.URI

/** data.go.kr 인증키는 Encoding(URL-인코딩) 형태 그대로 사용 — 재인코딩하면 키가 거부돼 URI 를 문자열로 조립한다. */
@Component
class DataGoKrSafetySpotClient(
    @Value("\${bueongi.datagokr.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(DataGoKrSafetySpotClient::class.java)
    private val serviceKey: String = serviceKey.trim().removeSurrounding("'").removeSurrounding("\"")

    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    fun fetchCctv(): List<DataGoKrPoint>? = fetchAll(DataGoKrApiSpec.CCTV_ENDPOINT, CCTV_FIELDS)

    fun fetchEmergencyBells(): List<DataGoKrPoint>? = fetchAll(DataGoKrApiSpec.BELL_ENDPOINT, BELL_FIELDS)

    fun fetchSafehouses(): List<DataGoKrPoint>? = fetchAll(DataGoKrApiSpec.SAFEHOUSE_ENDPOINT, SAFEHOUSE_FIELDS)

    fun fetchSecurityLamps(): List<DataGoKrPoint>? = fetchAll(DataGoKrApiSpec.SECURITY_LAMP_ENDPOINT, SECURITY_LAMP_FIELDS)

    private fun fetchAll(endpoint: String, fields: FieldNames): List<DataGoKrPoint>? {
        check(isEnabled) { "data.go.kr 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points: ArrayList<DataGoKrPoint> = ArrayList<DataGoKrPoint>()
        var pageNo: Int = 1
        var received: Long = 0L
        var totalCount: Long = Long.MAX_VALUE

        while (pageNo <= DataGoKrApiSpec.MAX_PAGES && received < totalCount) {
            val envelope: DataGoKrEnvelope = fetchPage(endpoint, pageNo) ?: return null
            val response: DataGoKrResponse = envelope.payload()
            val header: DataGoKrHeader? = response.header
            val code: String? = header?.resultCode
            if (!code.isNullOrBlank() && code !in DataGoKrApiSpec.SUCCESS_CODES) {
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

            if (rows.isEmpty() || rows.size < DataGoKrApiSpec.PAGE_SIZE) {
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

    /**
     * 페이지 1건을 받는다. 일시 장애(5xx·타임아웃·연결 실패)는 백오프 후 재시도하고,
     * 인증 오류 같은 영구 실패(4xx)는 즉시 포기한다. 재시도까지 실패하면 null(=갱신 중단).
     *
     * 전량 수집은 페이지가 수천 건이라 한 페이지의 일시 장애로 전체를 버리면 갱신이 사실상 성공하지 못한다.
     */
    private fun fetchPage(endpoint: String, pageNo: Int): DataGoKrEnvelope? {
        var attempt: Int = 1
        while (true) {
            try {
                return restClient.get()
                    .uri(URI.create("$endpoint?serviceKey=$serviceKey&pageNo=$pageNo&numOfRows=${DataGoKrApiSpec.PAGE_SIZE}&type=json"))
                    .retrieve()
                    .body(DataGoKrEnvelope::class.java)
            } catch (e: HttpClientErrorException) {
                // 예외 메시지에 URL(serviceKey 포함)이 섞일 수 있어 마스킹
                log.warn(
                    "data.go.kr 영구 실패(endpoint={}, page={}): {} — 갱신 중단(기존 데이터 유지)",
                    endpoint, pageNo, sanitize(e.message),
                )
                return null
            } catch (e: Exception) {
                if (attempt >= DataGoKrApiSpec.MAX_ATTEMPTS_PER_PAGE) {
                    log.warn(
                        "data.go.kr 호출 실패(endpoint={}, page={}, 시도={}회): {} — 갱신 중단(기존 데이터 유지)",
                        endpoint, pageNo, attempt, sanitize(e.message),
                    )
                    return null
                }
                val backoffMs: Long = DataGoKrApiSpec.RETRY_BASE_DELAY_MS * attempt
                log.warn(
                    "data.go.kr 일시 실패(endpoint={}, page={}, 시도={}회): {} — {}ms 후 재시도",
                    endpoint, pageNo, attempt, sanitize(e.message), backoffMs,
                )
                Thread.sleep(backoffMs)
                attempt += 1
            }
        }
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
