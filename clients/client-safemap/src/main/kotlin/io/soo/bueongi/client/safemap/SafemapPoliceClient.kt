package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.databind.JsonNode
import io.soo.bueongi.support.util.Wgs84Point
import io.soo.bueongi.support.util.resolveKoreanWgs84OrNull
import java.net.URI
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** 생활안전지도 치안시설(IF_0036). 인증키는 URL-인코딩분 그대로 써야 해 URI 를 문자열로 조립한다. */
@Component
class SafemapPoliceClient(
    @Value("\${bueongi.safemap.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(SafemapPoliceClient::class.java)
    private val serviceKey: String = serviceKey.trim()
    private val restClient: RestClient = restClientBuilder.clone().build()

    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    /** 계약: null = 수집 실패 — 호출자는 기존 데이터를 유지한다. 빈 목록과 다르다. */
    fun fetchPoliceFacilities(): List<SafemapPolicePoint>? {
        check(isEnabled) { "생활안전지도 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points: ArrayList<SafemapPolicePoint> = ArrayList()
        var received: Long = 0L
        var totalCount: Long? = null

        for (pageNo in 1..MAX_PAGES) {
            val body: JsonNode = fetchPage(pageNo) ?: return null
            if (!isSuccess(body)) {
                return null
            }
            val rows: List<JsonNode> = findPoliceRows(body)
            val pageTotal: Long? = body.findValue("totalCount")?.asLong()
            if (pageTotal != null && pageTotal >= 0) {
                totalCount = pageTotal
            }
            received += rows.size
            addPoints(rows, points)
            if (isLastPage(rows.size, received, totalCount)) {
                break
            }
        }
        return points
    }

    private fun fetchPage(pageNo: Int): JsonNode? {
        val url: String = "$ENDPOINT?serviceKey=$serviceKey" +
            "&pageNo=$pageNo&numOfRows=$PAGE_SIZE&returnType=JSON"
        val body: JsonNode? = try {
            restClient.get().uri(URI.create(url)).retrieve().body(JsonNode::class.java)
        } catch (e: Exception) {
            log.warn("치안시설 호출 실패(page={}): {} — 수집 중단", pageNo, sanitize(e.message))
            return null
        }
        if (body == null) {
            log.warn("치안시설 빈 응답(page={}) — 수집 중단", pageNo)
        }
        return body
    }

    private fun isSuccess(body: JsonNode): Boolean {
        val code: String? = body.findValue("resultCode")?.asText()?.trim()
        if (code.isNullOrBlank() || code in SUCCESS_CODES) {
            return true
        }
        val message: String = body.findValue("resultMsg")?.asText().orEmpty()
        log.warn("치안시설 resultCode={}({}) — 수집 중단", code, message)
        return false
    }

    /**
     * 봉투 구조가 문서에 없고 인증키를 못 받아 실측도 못 했다 — 확정되면 응답 DTO 로 바꾼다.
     * 그때까지는 치안시설 필드를 가진 객체 배열을 찾아 내려간다.
     */
    private fun findPoliceRows(node: JsonNode): List<JsonNode> {
        if (node.isArray && isPoliceRows(node)) {
            return node.toList()
        }
        for (child in node) {
            val found: List<JsonNode> = findPoliceRows(child)
            if (found.isNotEmpty()) {
                return found
            }
        }
        return emptyList()
    }

    private fun isPoliceRows(node: JsonNode): Boolean {
        for (child in node) {
            if (!child.isObject) {
                continue
            }
            if (child.has("fclty_nm") || (child.has("x") && child.has("y"))) {
                return true
            }
        }
        return false
    }

    private fun addPoints(rows: List<JsonNode>, into: MutableList<SafemapPolicePoint>) {
        for (row in rows) {
            val point: Wgs84Point = row.coordinate() ?: continue
            into.add(
                SafemapPolicePoint(
                    lat = point.lat,
                    lng = point.lng,
                    name = row.text("fclty_nm"),
                    address = row.text("rn_adres") ?: row.text("adres"),
                    phone = row.text("telno"),
                    sigungu = row.text("sgg_cd"),
                ),
            )
        }
    }

    /** 서버가 numOfRows 보다 적게 줄 수 있어 건수만으로 끊지 않고 totalCount 도 함께 본다. */
    private fun isLastPage(rowCount: Int, received: Long, totalCount: Long?): Boolean {
        if (rowCount == 0) {
            return true
        }
        if (totalCount != null) {
            return received >= totalCount
        }
        return rowCount < PAGE_SIZE
    }

    private fun sanitize(message: String?): String =
        (message ?: "원인 미상").replace(serviceKey, "***")

    private companion object {
        const val ENDPOINT: String = "https://safemap.go.kr/openapi2/IF_0036"

        const val PAGE_SIZE: Int = 1000

        /** 무한 루프 방지 — 전국 치안시설은 수천 건 규모다. */
        const val MAX_PAGES: Int = 500

        val SUCCESS_CODES: Set<String> = setOf("0", "00")
    }
}

/** 좌표계가 문서에 없어(EPSG:5179 통례) 값 범위로 자동판별한다. tmp_x/tmp_y 는 원천이 쓰는 대체 컬럼명이다. */
private fun JsonNode.coordinate(): Wgs84Point? =
    resolveKoreanWgs84OrNull(text("x"), text("y"))
        ?: resolveKoreanWgs84OrNull(text("tmp_x"), text("tmp_y"))

private fun JsonNode.text(field: String): String? {
    val value: String = get(field)?.asText()?.trim() ?: return null
    if (value.isEmpty() || value == "null") {
        return null
    }
    return value
}
