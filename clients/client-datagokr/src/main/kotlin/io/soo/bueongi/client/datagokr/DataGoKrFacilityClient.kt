package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * 공공데이터포털(data.go.kr) 행안부 표준 조회서비스 클라이언트 — 시설 데이터의 **라이브 갱신** 원천.
 *
 * 지원 서비스(모두 WGS84 좌표를 직접 제공해 좌표 변환이 불필요한 것만 라이브 대상으로 한다):
 * - CCTV: `/1741000/cctv_info/info` (파이프라인 소스 B-1과 동일 엔드포인트)
 * - 안전비상벨: `/1741000/emergency_call_box_info/info` (D-1과 동일)
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
    private val log: Logger = LoggerFactory.getLogger(DataGoKrFacilityClient::class.java)
    private val serviceKey: String = serviceKey.trim().removeSurrounding("'").removeSurrounding("\"")

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

    /** 응답 body.items 를 행 배열로 정규화 — 배열이거나 `{item: [...]}`/`{item: {...}}` 형태 모두 수용한다. */
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

        /** 표준 조회서비스 페이지당 최대 건수. */
        const val PAGE_SIZE: Int = 100

        /** 폭주 방지 상한(100행 × 5000페이지 = 50만 행) — 전국 CCTV 약 38만행을 전량 수집한다. */
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

        /**
         * 여성안심지킴이집 표준데이터. 점포명(storNm)이 대표 표기이고, 표준데이터 공통 규격인
         * latitude/longitude/rdnmadr 를 1순위로 하되 한글 컬럼 응답(위도/경도 등) 폴백을 둔다
         * (키 활성화 후 실응답으로 1순위 확정 예정 — 폴백이 있어 어느 쪽이든 동작).
         */
        private val SAFEHOUSE_FIELDS: FieldNames = FieldNames(
            lat = listOf("latitude", "위도"), lng = listOf("longitude", "경도"),
            name = listOf("storNm", "institutionNm", "업소명", "점포명"),
            address = listOf("rdnmadr", "lnmadr", "소재지도로명주소"), sigungu = emptyList(),
        )
    }
}



/** 행 JsonNode → [DataGoKrPoint]. 좌표가 숫자로 안 읽히면 null(건너뜀). */
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

/** 후보 필드명 중 첫 번째로 값이 있는 것을 돌려준다. */
private fun JsonNode.text(candidates: List<String>): String? =
    candidates.firstNotNullOfOrNull { field ->
        get(field)?.asText()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    }
