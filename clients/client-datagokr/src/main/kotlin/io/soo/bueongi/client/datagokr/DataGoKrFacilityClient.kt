package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * 공공데이터포털(data.go.kr) 행안부 표준 조회서비스 클라이언트 — 시설 데이터의 **라이브 갱신** 원천.
 *
 * 지원 서비스(모두 WGS84 좌표를 직접 제공해 좌표 변환이 불필요한 것만 라이브 대상으로 한다):
 * - CCTV: `/1741000/cctv_info` (파이프라인 소스 B-1과 동일 엔드포인트)
 * - 안전비상벨: `/1741000/emergency_call_box_info` (D-1과 동일)
 * - 여성안심지킴이집: `openapi/tn_pubr_public_female_safety_prtchouse_api` (표준데이터, B-2)
 * 좌표 변환·지오코딩이 필요한 나머지 종류(주소만 주는 B-3 등)는 파이프라인(월 1회 CSV/CDN 산출) 담당으로 남긴다.
 *
 * `DATA_GO_KR_SERVICE_KEY`(client-datagokr.yml: `bueongi.datagokr.service-key`)가 설정되면 [isEnabled]=true.
 * 키는 **Encoding(URL-인코딩된) 형태**를 그대로 받으며 재인코딩하지 않는다(이중 인코딩 시 키 거부됨) —
 * 그래서 URI 를 문자열로 조립해 [URI.create] 로 넘긴다.
 *
 * 실패 의미론: 페이지 순회 중 HTTP/파싱/resultCode 오류가 하나라도 나면 **전체를 null 로 반환**한다.
 * 파이프라인(부분수집 반환)과 달리 런타임 인덱스 교체의 입력이므로, 부분 데이터로 교체해 데이터가
 * 소리 없이 줄어드는 것을 막는다 — null 이면 상위(FacilityRefreshService)가 기존 데이터를 유지한다.
 */
@Component
class DataGoKrFacilityClient(
    @Value("\${bueongi.datagokr.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(DataGoKrFacilityClient::class.java)
    private val serviceKey: String = serviceKey.trim()

    /** 키가 설정돼 있으면 true. false 면 호출하지 말 것(상위가 라이브 갱신 자체를 건너뛴다). */
    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    /** 전국 CCTV 전량. 실패 시 null(기존 데이터 유지 신호). */
    fun fetchCctv(): List<DataGoKrPoint>? = fetchAll(CCTV_ENDPOINT, CCTV_FIELDS)

    /** 전국 안전비상벨 전량. 실패 시 null(기존 데이터 유지 신호). */
    fun fetchEmergencyBells(): List<DataGoKrPoint>? = fetchAll(BELL_ENDPOINT, BELL_FIELDS)

    /** 전국 여성안심지킴이집 전량. 실패 시 null(기존 데이터 유지 신호). */
    fun fetchSafehouses(): List<DataGoKrPoint>? = fetchAll(SAFEHOUSE_ENDPOINT, SAFEHOUSE_FIELDS)

    /**
     * pageNo 순회로 전량 수집한다: `{endpoint}?serviceKey=&pageNo=&numOfRows=1000&type=json`.
     * 종료 조건: 빈 페이지/마지막 페이지(수신 < numOfRows)/totalCount 도달/[MAX_PAGES] 상한.
     * 좌표를 못 읽는 행은 조용히 건너뛴다(불량 행이 전체 갱신을 막지 않게).
     */
    private fun fetchAll(endpoint: String, fields: FieldNames): List<DataGoKrPoint>? {
        check(isEnabled) { "data.go.kr 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points = ArrayList<DataGoKrPoint>()
        var pageNo = 1
        var received = 0L
        var totalCount = Long.MAX_VALUE

        while (pageNo <= MAX_PAGES && received < totalCount) {
            val envelope = try {
                restClient.get()
                    .uri(URI.create("$endpoint?serviceKey=$serviceKey&pageNo=$pageNo&numOfRows=$PAGE_SIZE&type=json"))
                    .retrieve()
                    .body(DataGoKrEnvelope::class.java)
            } catch (e: Exception) {
                // 시크릿(serviceKey) 비노출 — 예외 메시지에 URL(키 포함)이 섞일 수 있어 마스킹한다.
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
            val code = envelope.response?.header?.resultCode
            if (!code.isNullOrBlank() && code != "00") {
                log.warn(
                    "data.go.kr resultCode={}({}) (endpoint={}) — 갱신 중단(기존 데이터 유지)",
                    code, envelope.response?.header?.resultMsg ?: "", endpoint,
                )
                return null
            }

            val rows = extractItems(envelope.response?.body?.items)
            envelope.response?.body?.totalCount?.let { totalCount = it }
            received += rows.size
            rows.mapNotNullTo(points) { it.toPoint(fields) }

            if (rows.isEmpty() || rows.size < PAGE_SIZE) {
                break
            }
            pageNo += 1
        }
        return points
    }

    /** 응답 body.items 를 행 배열로 정규화 — 배열이거나 `{item: [...]}`/`{item: {...}}` 형태 모두 수용한다. */
    private fun extractItems(items: JsonNode?): List<JsonNode> = when {
        items == null -> emptyList()
        items.isArray -> items.toList()
        items.isObject && items.has("item") -> {
            val inner = items.get("item")
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
        const val CCTV_ENDPOINT = "https://apis.data.go.kr/1741000/cctv_info"
        const val BELL_ENDPOINT = "https://apis.data.go.kr/1741000/emergency_call_box_info"
        const val SAFEHOUSE_ENDPOINT = "https://api.data.go.kr/openapi/tn_pubr_public_female_safety_prtchouse_api"

        /** 표준 조회서비스 페이지당 최대 건수. */
        const val PAGE_SIZE = 1000

        /** 폭주 방지 상한(1000행 × 1000페이지 = 100만 행) — 전국 CCTV(약 37만행)에 충분한 여유. */
        const val MAX_PAGES = 1000

        // 서비스별 원천 필드명(파이프라인 LIVE_SPEC B-1/D-1/B-2와 동일 매핑). 각 필드는 후보를 순서대로
        // 시도한다 — 표준데이터 계열은 영문 camelCase 가 규격이지만 서비스·시기별 편차가 있어 폴백을 둔다.
        private val CCTV_FIELDS = FieldNames(
            lat = listOf("latitude"), lng = listOf("longitude"),
            name = listOf("institutionNm"), address = listOf("rdnmadr"), sigungu = emptyList(),
        )
        private val BELL_FIELDS = FieldNames(
            lat = listOf("lat"), lng = listOf("lon"),
            name = listOf("mng_inst"), address = listOf("rn_adres"), sigungu = listOf("sgg_cd"),
        )

        /**
         * 여성안심지킴이집 표준데이터. 점포명(storNm)이 대표 표기이고, 표준데이터 공통 규격인
         * latitude/longitude/rdnmadr 를 1순위로 하되 한글 컬럼 응답(위도/경도 등) 폴백을 둔다
         * (키 활성화 후 실응답으로 1순위 확정 예정 — 폴백이 있어 어느 쪽이든 동작).
         */
        private val SAFEHOUSE_FIELDS = FieldNames(
            lat = listOf("latitude", "위도"), lng = listOf("longitude", "경도"),
            name = listOf("storNm", "institutionNm", "업소명", "점포명"),
            address = listOf("rdnmadr", "lnmadr", "소재지도로명주소"), sigungu = emptyList(),
        )
    }
}

/** 조회서비스 한 행의 정규화 결과(WGS84). 도메인(FacilityPoint) 매핑·좌표 범위 검증은 상위(core-api)가 한다. */
data class DataGoKrPoint(
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val address: String? = null,
    val sigungu: String? = null,
)

/** 서비스별 원천 필드명 집합 — 필드마다 후보 목록을 순서대로 시도한다(빈 목록 = 미제공). */
private data class FieldNames(
    val lat: List<String>,
    val lng: List<String>,
    val name: List<String>,
    val address: List<String>,
    val sigungu: List<String>,
)

/** 행 JsonNode → [DataGoKrPoint]. 좌표가 숫자로 안 읽히면 null(건너뜀). */
private fun JsonNode.toPoint(fields: FieldNames): DataGoKrPoint? {
    val lat = text(fields.lat)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val lng = text(fields.lng)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    return DataGoKrPoint(
        lat = lat,
        lng = lng,
        name = text(fields.name),
        address = text(fields.address),
        sigungu = text(fields.sigungu),
    )
}

/** 후보 필드명 중 첫 번째로 값이 있는 것을 돌려준다. */
private fun JsonNode.text(candidates: List<String>): String? =
    candidates.firstNotNullOfOrNull { field ->
        get(field)?.asText()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    }

// ---- 응답 DTO (표준 조회서비스 공통 envelope) ----

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGoKrEnvelope(val response: DataGoKrResponse? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGoKrResponse(val header: DataGoKrHeader? = null, val body: DataGoKrBody? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGoKrHeader(val resultCode: String? = null, val resultMsg: String? = null)

/** items 는 배열/`{item:...}` 두 형태가 있어 [JsonNode] 로 받고 클라이언트가 정규화한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGoKrBody(val items: JsonNode? = null, val totalCount: Long? = null)
