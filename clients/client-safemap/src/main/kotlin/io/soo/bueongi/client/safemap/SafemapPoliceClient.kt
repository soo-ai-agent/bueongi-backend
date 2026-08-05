package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * 생활안전지도(safemap.go.kr) 치안시설 정보조회(IF_0036) 클라이언트 — 경찰청 제공,
 * 전국 지역경찰관서(지구대·파출소·치안센터)의 주소·전화·좌표. 갱신주기 1년.
 *
 * `SAFEMAP_SERVICE_KEY`(client-safemap.yml: `bueongi.safemap.service-key`)가 설정되면 [isEnabled]=true.
 * 키는 생활안전지도 발급분(URL-인코딩)을 그대로 쓰며 재인코딩하지 않는다.
 *
 * 좌표 처리: 응답의 x/y 는 문자열이고 좌표계가 문서에 명시돼 있지 않다(생활안전지도 계열은 EPSG:5179 가
 * 통례). 값 범위로 자동 판별한다 — 경위도 범위(WGS84)면 그대로, UTM-K 미터 범위면 [Epsg5179] 역변환,
 * 어느 쪽도 아니면 tmp_x/tmp_y 로 재시도 후 그 행을 버린다(불량 행이 전체 갱신을 막지 않게).
 *
 * 실패 의미론은 data.go.kr 클라이언트와 동일: 페이지 순회 중 오류가 나면 전체 null(부분 교체 금지) —
 * 상위(FacilityRefreshService)가 기존 데이터를 유지한다.
 */
@Component
class SafemapPoliceClient(
    @Value("\${bueongi.safemap.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(SafemapPoliceClient::class.java)
    private val serviceKey: String = serviceKey.trim()

    /** 키가 설정돼 있으면 true. false 면 호출하지 말 것(상위가 라이브 갱신 자체를 건너뛴다). */
    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    /**
     * 전국 치안시설 전량. 실패 시 null(기존 데이터 유지 신호).
     *
     * 페이지 순회는 totalCount 를 1차 기준으로 한다 — 서버가 요청 numOfRows 보다 적게 돌려줘도
     * (페이지 상한이 낮은 서비스 방어) totalCount 에 도달할 때까지 계속 넘긴다.
     */
    fun fetchPoliceFacilities(): List<SafemapPoliceFacility>? {
        check(isEnabled) { "생활안전지도 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val facilities: ArrayList<SafemapPoliceFacility> = ArrayList<SafemapPoliceFacility>()
        var pageNo: Int = 1
        var received: Long = 0L
        var totalCount: Long? = null

        while (pageNo <= MAX_PAGES) {
            val body: JsonNode? = try {
                restClient.get()
                    .uri(URI.create("$ENDPOINT?serviceKey=$serviceKey&pageNo=$pageNo&numOfRows=$PAGE_SIZE&returnType=JSON"))
                    .retrieve()
                    .body(JsonNode::class.java)
            } catch (e: Exception) {
                // 시크릿(serviceKey) 비노출 — 예외 메시지에 URL(키 포함)이 섞일 수 있어 마스킹한다.
                log.warn("safemap 호출 실패(page={}): {} — 갱신 중단(기존 데이터 유지)", pageNo, sanitize(e.message))
                return null
            }
            if (body == null) {
                log.warn("safemap 빈 응답(page={}) — 갱신 중단(기존 데이터 유지)", pageNo)
                return null
            }
            val code: String? = body.findValue("resultCode")?.asText()?.trim()
            if (!code.isNullOrBlank() && code != "0" && code != "00") {
                log.warn(
                    "safemap resultCode={}({}) — 갱신 중단(기존 데이터 유지)",
                    code, body.findValue("resultMsg")?.asText() ?: "",
                )
                return null
            }

            val rows: List<JsonNode> = findFacilityRows(body)
            val parsedTotalCount: Long? = body.findValue("totalCount")?.asLong()
            if (parsedTotalCount != null && parsedTotalCount >= 0) {
                totalCount = parsedTotalCount
            }
            received += rows.size
            rows.mapNotNullTo(facilities) { it.toFacility() }

            val total: Long? = totalCount
            when {
                rows.isEmpty() -> break
                total != null && received >= total -> break
                total == null && rows.size < PAGE_SIZE -> break
            }
            pageNo += 1
        }
        return facilities
    }

    /**
     * 응답에서 시설 행 배열을 찾는다 — envelope 구조(중첩 위치·items/item 래핑)가 문서에 확정돼 있지 않아,
     * "치안시설 필드(fclty_nm 또는 x·y)를 가진 객체들의 배열"을 재귀 탐색한다.
     */
    private fun findFacilityRows(node: JsonNode): List<JsonNode> {
        if (node.isArray) {
            val objects: List<JsonNode> = node.filter { it.isObject }
            if (objects.isNotEmpty() && objects.any { it.has("fclty_nm") || (it.has("x") && it.has("y")) }) {
                return objects
            }
            for (child in node) {
                val found: List<JsonNode> = findFacilityRows(child)
                if (found.isNotEmpty()) {
                    return found
                }
            }
            return emptyList()
        }
        if (node.isObject) {
            for (child in node) {
                val found: List<JsonNode> = findFacilityRows(child)
                if (found.isNotEmpty()) {
                    return found
                }
            }
        }
        return emptyList()
    }

    /** 행 → 시설(WGS84 확정). 좌표를 판별 못 하면 null(건너뜀). */
    private fun JsonNode.toFacility(): SafemapPoliceFacility? {
        val point: Wgs84Point = resolveKoreanWgs84OrNull(text("x"), text("y"))
            ?: resolveKoreanWgs84OrNull(text("tmp_x"), text("tmp_y"))
            ?: return null
        return SafemapPoliceFacility(
            lat = point.lat,
            lng = point.lng,
            name = text("fclty_nm"),
            address = text("rn_adres") ?: text("adres"),
            phone = text("telno"),
            sigungu = text("sgg_cd"),
        )
    }

    private fun JsonNode.text(field: String): String? =
        get(field)?.asText()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun sanitize(message: String?): String =
        (message ?: "원인 미상").replace(serviceKey, "***")

    companion object {
        const val ENDPOINT: String = "https://safemap.go.kr/openapi2/IF_0036"

        /** 페이지당 요청 건수 — 서버가 상한을 낮게 잡아도 totalCount 순회로 전량을 받는다. */
        const val PAGE_SIZE: Int = 1000

        /** 폭주 방지 상한 — 전국 지역경찰관서(수천 관서)에 충분한 여유. */
        const val MAX_PAGES: Int = 500
    }
}
