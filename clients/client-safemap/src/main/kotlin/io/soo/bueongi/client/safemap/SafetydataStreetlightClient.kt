package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/** safetydata DSSP-IF-00084: 인증키는 URL-인코딩분 그대로(재인코딩 금지), XMAP_CRTS/YMAP_CRTS 좌표계는 문서 미명시라 값 범위로 자동판별한다. */
@Component
class SafetydataStreetlightClient(
    @Value("\${bueongi.safetydata.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
    @Value("\${bueongi.safetydata.base-url:${SafetydataApiSpec.DEFAULT_BASE_URL}}") baseUrl: String = SafetydataApiSpec.DEFAULT_BASE_URL,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetydataStreetlightClient::class.java)
    private val serviceKey: String = serviceKey.trim()
    private val endpoint: String = "${baseUrl.trimEnd('/')}${SafetydataApiSpec.PATH}"

    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    /** 서버가 numOfRows 보다 적게 돌려줘도 totalCount 도달까지 페이지를 넘긴다(페이지 상한이 낮은 서비스 방어). */
    fun fetchStreetlights(): List<SafetydataStreetlight>? {
        check(isEnabled) { "재난안전데이터(safetydata) 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val streetlights: ArrayList<SafetydataStreetlight> = ArrayList<SafetydataStreetlight>()
        var pageNo: Int = 1
        var received: Long = 0L
        var totalCount: Long? = null

        while (pageNo <= SafetydataApiSpec.MAX_PAGES) {
            val body: JsonNode? = try {
                restClient.get()
                    .uri(URI.create("$endpoint?serviceKey=$serviceKey&pageNo=$pageNo&numOfRows=${SafetydataApiSpec.PAGE_SIZE}&returnType=json"))
                    .retrieve()
                    .body(JsonNode::class.java)
            } catch (e: Exception) {
                // 예외 메시지에 URL(serviceKey 포함)이 섞일 수 있어 마스킹
                log.warn("safetydata 가로등 호출 실패(page={}): {} — 갱신 중단(기존 데이터 유지)", pageNo, sanitize(e.message))
                return null
            }
            if (body == null) {
                log.warn("safetydata 가로등 빈 응답(page={}) — 갱신 중단(기존 데이터 유지)", pageNo)
                return null
            }
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
                total == null && rows.size < SafetydataApiSpec.PAGE_SIZE -> break
            }
            pageNo += 1
        }
        val finalTotal: Long? = totalCount
        if (finalTotal != null && received < finalTotal) {
            log.warn("safetydata 가로등 페이지 상한(SafetydataApiSpec.MAX_PAGES={}) 도달 — {}/{}건만 수집", SafetydataApiSpec.MAX_PAGES, received, finalTotal)
        }
        return streetlights
    }

    // 응답 envelope 구조가 문서에 미확정이라 좌표 필드를 가진 객체 배열을 재귀 탐색한다
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

}
