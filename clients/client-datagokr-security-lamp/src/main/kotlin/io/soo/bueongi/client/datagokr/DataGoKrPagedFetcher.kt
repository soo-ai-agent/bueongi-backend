package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.databind.JsonNode
import io.soo.bueongi.client.datagokr.query.DataGoKrFieldNames
import io.soo.bueongi.client.datagokr.response.DataGoKrEnvelope
import io.soo.bueongi.client.datagokr.response.DataGoKrResponse
import io.soo.bueongi.support.logging.NO_RESPONSE_STATUS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.URI

// 인증키는 Encoding(URL-인코딩) 형태 그대로 써야 한다 — 재인코딩하면 거부돼 URI 를 문자열로 조립한다.
@Component
class DataGoKrPagedFetcher(
    @Value("\${bueongi.datagokr.service-key:}") serviceKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(DataGoKrPagedFetcher::class.java)
    private val serviceKey: String = serviceKey.trim().removeSurrounding("'").removeSurrounding("\"")

    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    // null = 수집 실패(부분 실패 포함), 빈 목록과 다르다 — 호출자는 기존 데이터를 유지한다.
    fun fetchAll(endpoint: String, fields: DataGoKrFieldNames, municipalityCode: String? = null): List<DataGoKrPoint>? {
        check(isEnabled) { "data.go.kr 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points: ArrayList<DataGoKrPoint> = ArrayList<DataGoKrPoint>()
        var pageNo: Int = 1
        var received: Long = 0L
        var totalCount: Long = Long.MAX_VALUE

        while (pageNo <= DataGoKrApiSpec.MAX_PAGES && received < totalCount) {
            val envelope: DataGoKrEnvelope = fetchPage(endpoint, pageNo, municipalityCode) ?: return null
            val response: DataGoKrResponse = envelope.payload()
            val code: String = response.header.resultCode
            if (code.isNotBlank() && code !in DataGoKrApiSpec.SUCCESS_CODES) {
                log.warn(
                    failureLine(
                        endpoint = endpoint,
                        cause = "resultCode=$code",
                        paging = "page=$pageNo",
                    ),
                )
                return null
            }

            val rows: List<JsonNode> = extractItems(response.body.items)
            val pageTotalCount: Long? = response.body.totalCount
            if (pageTotalCount != null) {
                totalCount = pageTotalCount
            }
            received += rows.size
            rows.mapNotNullTo(points) { it.toPoint(fields) }

            if (rows.isEmpty() || rows.size < DataGoKrApiSpec.STANDARD_PAGE_SIZE) {
                if (totalCount == Long.MAX_VALUE) {
                    totalCount = received
                }
                break
            }
            pageNo += 1
        }
        if (received < totalCount) {
            log.warn(
                failureLine(
                    endpoint = endpoint,
                    cause = "페이지 상한 도달(받은 ${received}건 / 총 ${totalCount}건)",
                    paging = "page=${DataGoKrApiSpec.MAX_PAGES}",
                ),
            )
            return null
        }
        return points
    }

    // 전량 수집은 페이지가 수천 건이라 한 페이지의 일시 장애로 전체를 버리면 갱신이 성립하지 않는다.
    private fun fetchPage(endpoint: String, pageNo: Int, municipalityCode: String?): DataGoKrEnvelope? {
        var attempt: Int = 1
        while (true) {
            try {
                return restClient.get()
                    .uri(URI.create(pageUrl(endpoint, pageNo, municipalityCode)))
                    .retrieve()
                    .body(DataGoKrEnvelope::class.java)
            } catch (e: HttpClientErrorException) {
                log.warn(
                    failureLine(
                        endpoint = endpoint,
                        cause = e.javaClass.simpleName,
                        paging = "page=$pageNo 시도=${attempt}회",
                        result = "${e.statusCode.value()} GET",
                    ),
                )
                return null
            } catch (e: Exception) {
                if (attempt >= DataGoKrApiSpec.MAX_ATTEMPTS_PER_PAGE) {
                    log.warn(
                        failureLine(
                            endpoint = endpoint,
                            cause = e.javaClass.simpleName,
                            paging = "page=$pageNo 시도=${attempt}회",
                        ),
                    )
                    return null
                }
                val backoffMs: Long = DataGoKrApiSpec.RETRY_BASE_DELAY_MS * attempt
                log.warn(
                    failureLine(
                        endpoint = endpoint,
                        cause = e.javaClass.simpleName,
                        paging = "page=$pageNo 시도=${attempt}회",
                        action = "${backoffMs}ms 후 재시도",
                    ),
                )
                Thread.sleep(backoffMs)
                attempt += 1
            }
        }
    }

    // 표준데이터 계열은 `type` 만 받는다 — `returnType` 을 주면 resultCode 10 으로 거부한다(실측).
    private fun pageUrl(endpoint: String, pageNo: Int, municipalityCode: String?): String {
        val regionFilter: String =
            if (municipalityCode != null) {
                "&$PARAM_INSTITUTION_CODE=$municipalityCode"
            } else {
                ""
            }
        return "$endpoint?serviceKey=$serviceKey" +
            "&pageNo=$pageNo&numOfRows=${DataGoKrApiSpec.STANDARD_PAGE_SIZE}" +
            "&type=json" +
            regionFilter
    }

    private fun extractItems(items: JsonNode): List<JsonNode> =
        when {
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

    private fun failureLine(
        endpoint: String,
        cause: String,
        paging: String,
        action: String = STOPPED_ACTION,
        result: String = NO_RESPONSE_STATUS,
    ): String {
        return "$PROVIDER 수집 실패 | ${URI.create(endpoint).path} | $result | $paging | 원인=$cause | 조치=$action"
    }

    private companion object {
        const val PROVIDER: String = "data_go_kr"

        const val PARAM_INSTITUTION_CODE: String = "insttCode"
        const val STOPPED_ACTION: String = "갱신 중단(기존 데이터 유지)"
    }
}

private fun JsonNode.toPoint(fields: DataGoKrFieldNames): DataGoKrPoint? {
    val lat: Double = text(fields.lat)?.toDoubleOrNull() ?: return null
    val lng: Double = text(fields.lng)?.toDoubleOrNull() ?: return null
    if (!lat.isFinite() || !lng.isFinite()) {
        return null
    }
    return DataGoKrPoint(
        lat = lat,
        lng = lng,
        name = text(fields.name),
        address = text(fields.address),
        sigungu = text(fields.sigungu),
        phone = text(fields.phone),
    )
}

private fun JsonNode.text(candidates: List<String>): String? {
    for (field in candidates) {
        val value: String = get(field)?.asText()?.trim() ?: continue
        if (value.isNotEmpty() && value != "null") {
            return value
        }
    }
    return null
}
