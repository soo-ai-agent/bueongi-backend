package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/** safemap IF_0036: 인증키는 URL-인코딩분 그대로(재인코딩 금지), x/y 좌표계는 문서 미명시(EPSG:5179 통례)라 값 범위로 자동판별한다. */
@Component
class SafemapPoliceClient(
    @Value("\${bueongi.safemap.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(SafemapPoliceClient::class.java)
    private val serviceKey: String = serviceKey.trim()

    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    /** 서버가 numOfRows 보다 적게 돌려줘도 totalCount 도달까지 페이지를 넘긴다(페이지 상한이 낮은 서비스 방어). */
    fun fetchPoliceFacilities(): List<SafemapPolicePoint>? {
        check(isEnabled) { "생활안전지도 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val facilities: ArrayList<SafemapPolicePoint> = ArrayList<SafemapPolicePoint>()
        var pageNo: Int = 1
        var received: Long = 0L
        var totalCount: Long? = null

        while (pageNo <= SafemapApiSpec.MAX_PAGES) {
            val body: JsonNode? = try {
                restClient.get()
                    .uri(URI.create("${SafemapApiSpec.ENDPOINT}?serviceKey=$serviceKey&pageNo=$pageNo&numOfRows=${SafemapApiSpec.PAGE_SIZE}&returnType=JSON"))
                    .retrieve()
                    .body(JsonNode::class.java)
            } catch (e: Exception) {
                // 예외 메시지에 URL(serviceKey 포함)이 섞일 수 있어 마스킹
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

            val rows: List<JsonNode> = findSafetySpotRows(body)
            val parsedTotalCount: Long? = body.findValue("totalCount")?.asLong()
            if (parsedTotalCount != null && parsedTotalCount >= 0) {
                totalCount = parsedTotalCount
            }
            received += rows.size
            rows.mapNotNullTo(facilities) { it.toSafetySpot() }

            val total: Long? = totalCount
            when {
                rows.isEmpty() -> break
                total != null && received >= total -> break
                total == null && rows.size < SafemapApiSpec.PAGE_SIZE -> break
            }
            pageNo += 1
        }
        return facilities
    }

    // 응답 envelope 구조가 문서에 미확정이라 치안시설 필드를 가진 객체 배열을 재귀 탐색한다
    private fun findSafetySpotRows(node: JsonNode): List<JsonNode> {
        if (node.isArray) {
            val objects: List<JsonNode> = node.filter { it.isObject }
            if (objects.isNotEmpty() && objects.any { it.has("fclty_nm") || (it.has("x") && it.has("y")) }) {
                return objects
            }
            for (child in node) {
                val found: List<JsonNode> = findSafetySpotRows(child)
                if (found.isNotEmpty()) {
                    return found
                }
            }
            return emptyList()
        }
        if (node.isObject) {
            for (child in node) {
                val found: List<JsonNode> = findSafetySpotRows(child)
                if (found.isNotEmpty()) {
                    return found
                }
            }
        }
        return emptyList()
    }

    private fun JsonNode.toSafetySpot(): SafemapPolicePoint? {
        val point: Wgs84Point = resolveKoreanWgs84OrNull(text("x"), text("y"))
            ?: resolveKoreanWgs84OrNull(text("tmp_x"), text("tmp_y"))
            ?: return null
        return SafemapPolicePoint(
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

}
