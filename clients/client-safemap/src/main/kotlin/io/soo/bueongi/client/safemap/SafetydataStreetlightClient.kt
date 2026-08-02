package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * 재난안전데이터 공유 플랫폼(safetydata.go.kr) 공통POI 가로등(DSSP-IF-00084) 클라이언트 —
 * 행정안전부 제공, 전국 가로등 위치. 원 데이터 갱신주기는 1일이나 우리는 월 1회+기동 1회로 충분하다.
 *
 * `SAFETYDATA_SERVICE_KEY`(client-safemap.yml: `bueongi.safetydata.service-key`)가 설정되면 [isEnabled]=true.
 * 키는 발급분(URL-인코딩)을 그대로 쓰며 재인코딩하지 않는다(safemap 과 동일).
 *
 * 좌표(XMAP_CRTS/YMAP_CRTS)는 좌표계가 문서에 명시돼 있지 않아 값 범위로 자동 판별한다
 * ([resolveKoreanWgs84OrNull] — 경위도 그대로 / EPSG:5179 역변환). 판별 실패 행은 버린다(불량 행이 전체 갱신을 막지 않게).
 * 실패 의미론은 safemap/datagokr 과 동일: 페이지 순회 중 오류가 나면 전체 null(부분 교체 금지) —
 * 상위([io.soo.bueongi] 도메인 FacilityRefreshService)가 기존 데이터를 유지한다.
 */
@Component
class SafetydataStreetlightClient(
    @Value("\${bueongi.safetydata.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
    // 기본은 실 호스트. 스테이징/로컬 목 게이트를 가리키게 오버라이드할 수 있다(bueongi.safetydata.base-url).
    @Value("\${bueongi.safetydata.base-url:$DEFAULT_BASE_URL}") baseUrl: String = DEFAULT_BASE_URL,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetydataStreetlightClient::class.java)
    private val serviceKey: String = serviceKey.trim()
    private val endpoint: String = "${baseUrl.trimEnd('/')}$PATH"

    /** 키가 설정돼 있으면 true. false 면 호출하지 말 것(상위가 라이브 갱신 자체를 건너뛴다). */
    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    // connect/read 타임아웃은 전역 설정(application.yml: spring.http.client.*)이 이 빌더에 적용된다.
    private val restClient: RestClient = restClientBuilder.clone().build()

    /**
     * 전국 가로등 전량. 실패 시 null(기존 데이터 유지 신호).
     * 페이지 순회는 totalCount 를 1차 기준으로 하고, 상한(MAX_PAGES)에 닿으면 그만큼만 수집하며 로그로 표면화한다.
     */
    fun fetchStreetlights(): List<SafetydataStreetlight>? {
        check(isEnabled) { "재난안전데이터(safetydata) 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val streetlights: ArrayList<SafetydataStreetlight> = ArrayList<SafetydataStreetlight>()
        var pageNo: Int = 1
        var received: Long = 0L
        var totalCount: Long? = null

        while (pageNo <= MAX_PAGES) {
            val body: JsonNode? = try {
                restClient.get()
                    .uri(URI.create("$endpoint?serviceKey=$serviceKey&pageNo=$pageNo&numOfRows=$PAGE_SIZE&returnType=json"))
                    .retrieve()
                    .body(JsonNode::class.java)
            } catch (e: Exception) {
                // 시크릿(serviceKey) 비노출 — 예외 메시지에 URL(키 포함)이 섞일 수 있어 마스킹한다.
                log.warn("safetydata 가로등 호출 실패(page={}): {} — 갱신 중단(기존 데이터 유지)", pageNo, sanitize(e.message))
                return null
            }
            if (body == null) {
                log.warn("safetydata 가로등 빈 응답(page={}) — 갱신 중단(기존 데이터 유지)", pageNo)
                return null
            }
            // resultCode: 0/00 정상(숫자·문자 모두 수용), 그 외는 중단. 필드 위치는 envelope 편차에 대비해 재귀 탐색.
            val code: String? = body.findValue("resultCode")?.asText()?.trim()
            if (!code.isNullOrBlank() && code != "0" && code != "00") {
                log.warn(
                    "safetydata resultCode={}({}) — 갱신 중단(기존 데이터 유지)",
                    code, body.findValue("resultMsg")?.asText() ?: "",
                )
                return null
            }

            val rows: List<JsonNode> = findStreetlightRows(body)
            val parsedTotalCount: Long? = body.findValue("totalCount")?.asLong()
            if (parsedTotalCount != null && parsedTotalCount >= 0) {
                totalCount = parsedTotalCount
            }
            received += rows.size
            rows.mapNotNullTo(streetlights) { it.toStreetlight() }

            val total: Long? = totalCount
            when {
                rows.isEmpty() -> break
                total != null && received >= total -> break
                total == null && rows.size < PAGE_SIZE -> break
            }
            pageNo += 1
        }
        val finalTotal: Long? = totalCount
        if (finalTotal != null && received < finalTotal) {
            log.warn("safetydata 가로등 페이지 상한(MAX_PAGES={}) 도달 — {}/{}건만 수집", MAX_PAGES, received, finalTotal)
        }
        return streetlights
    }

    /**
     * 응답에서 가로등 행 배열을 찾는다 — envelope 구조(중첩·items/item 래핑)가 문서에 확정돼 있지 않아,
     * "좌표 필드(XMAP_CRTS·YMAP_CRTS)를 가진 객체들의 배열"을 재귀 탐색한다.
     */
    private fun findStreetlightRows(node: JsonNode): List<JsonNode> {
        if (node.isArray) {
            val objects: List<JsonNode> = node.filter { it.isObject }
            if (objects.isNotEmpty() && objects.any { it.has("XMAP_CRTS") && it.has("YMAP_CRTS") }) {
                return objects
            }
            for (child in node) {
                val found: List<JsonNode> = findStreetlightRows(child)
                if (found.isNotEmpty()) {
                    return found
                }
            }
            return emptyList()
        }
        if (node.isObject) {
            for (child in node) {
                val found: List<JsonNode> = findStreetlightRows(child)
                if (found.isNotEmpty()) {
                    return found
                }
            }
        }
        return emptyList()
    }

    /** 행 → 가로등(WGS84 확정). 좌표를 판별 못 하면 null(건너뜀). 가로등은 이름·전화가 없어 주소·시군구만 담는다. */
    private fun JsonNode.toStreetlight(): SafetydataStreetlight? {
        val point: Wgs84Point = resolveKoreanWgs84OrNull(text("XMAP_CRTS"), text("YMAP_CRTS")) ?: return null
        return SafetydataStreetlight(
            lat = point.lat,
            lng = point.lng,
            address = text("ROAD_NM_ADDR") ?: text("ADDR"),
            sigungu = text("STDG_SGG_CD"),
        )
    }

    private fun JsonNode.text(field: String): String? =
        get(field)?.asText()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun sanitize(message: String?): String =
        (message ?: "원인 미상").replace(serviceKey, "***")

    companion object {
        const val DEFAULT_BASE_URL: String = "https://www.safetydata.go.kr"
        const val PATH: String = "/V2/api/DSSP-IF-00084"

        /** 기본(실 호스트) 전체 엔드포인트 — 테스트·로그용. 실제 호출 URL 은 base-url + PATH 로 조립한다. */
        const val ENDPOINT: String = "$DEFAULT_BASE_URL$PATH"

        /** 페이지당 요청 건수 — 서버가 상한을 낮게 잡아도 totalCount 순회로 전량을 받는다. */
        const val PAGE_SIZE: Int = 1000

        /** 폭주 방지 상한 — 전국 가로등은 매우 많아, 상한에 닿으면 그만큼만 수집하고 로그로 알린다. */
        const val MAX_PAGES: Int = 1000
    }
}

/** 가로등 1건(WGS84 변환·검증 완료). 도메인(FacilityPoint) 매핑은 상위(core-api)가 한다. */
data class SafetydataStreetlight(
    val lat: Double,
    val lng: Double,
    /** 도로명주소(ROAD_NM_ADDR) 우선, 없으면 지번주소(ADDR). */
    val address: String? = null,
    /** 법정동 시군구코드(STDG_SGG_CD). */
    val sigungu: String? = null,
)
