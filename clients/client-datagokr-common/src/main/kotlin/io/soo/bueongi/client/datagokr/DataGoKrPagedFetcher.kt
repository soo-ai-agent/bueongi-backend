package io.soo.bueongi.client.datagokr

import io.soo.bueongi.core.enums.DataGoKrQueryDialect
import com.fasterxml.jackson.databind.JsonNode
import io.soo.bueongi.support.logging.ExceptionLogLine
import io.soo.bueongi.support.logging.IGNORED_EXCEPTION
import io.soo.bueongi.support.logging.NO_RESPONSE_STATUS
import io.soo.bueongi.support.logging.causeChainOf
import io.soo.bueongi.support.logging.maskSecrets
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * data.go.kr 공통 전송 — 페이징·재시도·봉투 해석을 한곳에서 맡는다.
 * 자원별 클라이언트(cctv·bell·safehouse·lamp)는 엔드포인트와 필드 매핑만 갖고 이것을 부른다.
 *
 * 인증키는 Encoding(URL-인코딩) 형태 그대로 사용 — 재인코딩하면 키가 거부돼 URI 를 문자열로 조립한다.
 */
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

    /** 계약: null = 수집 실패(부분 실패 포함) — 호출자는 기존 데이터를 유지한다. 빈 목록과 다르다. */
    fun fetchAll(
        endpoint: String,
        fields: DataGoKrFieldNames,
        query: DataGoKrQuery = DataGoKrQuery(),
        dialect: DataGoKrQueryDialect = DataGoKrQueryDialect.CONDITION,
    ): List<DataGoKrPoint>? {
        check(isEnabled) { "data.go.kr 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points: ArrayList<DataGoKrPoint> = ArrayList<DataGoKrPoint>()
        var pageNo: Int = 1
        var received: Long = 0L
        var totalCount: Long = Long.MAX_VALUE

        while (pageNo <= DataGoKrApiSpec.MAX_PAGES && received < totalCount) {
            val envelope: DataGoKrEnvelope = fetchPage(endpoint, pageNo, query, dialect) ?: return null
            val response: DataGoKrResponse = envelope.payload()
            val header: DataGoKrHeader? = response.header
            val code: String? = header?.resultCode
            if (!code.isNullOrBlank() && code !in DataGoKrApiSpec.SUCCESS_CODES) {
                log.warn(
                    failureLine(
                        endpoint = endpoint,
                        cause = "resultCode=$code(${maskSecrets(header.resultMsg.orEmpty())})",
                        paging = "page=$pageNo",
                    ),
                )
                return null
            }

            val rows: List<JsonNode> = extractItems(response.body?.items)
            response.body?.totalCount?.let { totalCount = it }
            received += rows.size
            rows.mapNotNullTo(points) { it.toPoint(fields) }

            if (rows.isEmpty() || rows.size < pageSizeOf(dialect)) {
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

    /**
     * 페이지 1건을 받는다. 일시 장애(5xx·타임아웃·연결 실패)는 백오프 후 재시도하고,
     * 인증 오류 같은 영구 실패(4xx)는 즉시 포기한다. 재시도까지 실패하면 null(=갱신 중단).
     *
     * 전량 수집은 페이지가 수천 건이라 한 페이지의 일시 장애로 전체를 버리면 갱신이 사실상 성공하지 못한다.
     */
    private fun fetchPage(
        endpoint: String,
        pageNo: Int,
        query: DataGoKrQuery,
        dialect: DataGoKrQueryDialect,
    ): DataGoKrEnvelope? {
        var attempt: Int = 1
        while (true) {
            try {
                return restClient.get()
                    .uri(URI.create(pageUrl(endpoint, pageNo, query, dialect)))
                    .retrieve()
                    .body(DataGoKrEnvelope::class.java)
            } catch (e: HttpClientErrorException) {
                // 예외 메시지에 URL(serviceKey 포함)이 섞일 수 있어 causeChainOf 가 마스킹한다.
                log.warn(
                    failureLine(
                        endpoint = endpoint,
                        cause = causeChainOf(e, serviceKey),
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
                            cause = causeChainOf(e, serviceKey),
                            paging = "page=$pageNo 시도=${attempt}회",
                        ),
                    )
                    return null
                }
                val backoffMs: Long = DataGoKrApiSpec.RETRY_BASE_DELAY_MS * attempt
                log.warn(
                    failureLine(
                        endpoint = endpoint,
                        cause = causeChainOf(e, serviceKey),
                        paging = "page=$pageNo 시도=${attempt}회",
                        action = "${backoffMs}ms 후 재시도",
                    ),
                )
                Thread.sleep(backoffMs)
                attempt += 1
            }
        }
    }

    /**
     * 응답 타입 파라미터 이름이 계열마다 다르다 — 1741000 은 `returnType`(과 `type`)을 받고,
     * 표준데이터는 `type` 만 받으며 `returnType` 을 주면 `resultCode 10` 으로 거부한다(실측).
     */
    private fun pageUrl(endpoint: String, pageNo: Int, query: DataGoKrQuery, dialect: DataGoKrQueryDialect): String {
        val responseType: String = if (dialect == DataGoKrQueryDialect.CONDITION) {
            "&returnType=json&type=json"
        } else {
            "&type=json"
        }
        return "$endpoint?serviceKey=$serviceKey" +
            "&pageNo=$pageNo&numOfRows=${pageSizeOf(dialect)}" +
            responseType +
            query.toQueryParams(dialect)
    }

    /** 표준데이터 계열은 한 번에 1,000건까지 준다 — 100 으로 부르면 호출이 10배가 된다(실측). */
    private fun pageSizeOf(dialect: DataGoKrQueryDialect): Int =
        if (dialect == DataGoKrQueryDialect.CONDITION) {
            DataGoKrApiSpec.PAGE_SIZE
        } else {
            DataGoKrApiSpec.STANDARD_PAGE_SIZE
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

    /**
     * 실패 한 건을 예외 줄 규격으로 옮긴다 — 바로 위의 송신 줄과 같은 자리에서 원천·경로가 보인다.
     * 대상 칸에는 경로만 적는다(엔드포인트는 전체 URL 로 들어온다).
     */
    private fun failureLine(
        endpoint: String,
        cause: String,
        paging: String,
        action: String = STOPPED_ACTION,
        result: String = NO_RESPONSE_STATUS,
    ): String = ExceptionLogLine(
        kind = IGNORED_EXCEPTION,
        counterpart = PROVIDER,
        subject = URI.create(endpoint).path,
        cause = cause,
        result = result,
        action = action,
        paging = paging,
    ).format()

    private companion object {
        /** 송신 줄의 원천 이름과 같아야 세로로 짝이 보인다([ExternalApiProvider.DATA_GO_KR]). */
        const val PROVIDER: String = "data_go_kr"
        const val STOPPED_ACTION: String = "갱신 중단(기존 데이터 유지)"
    }
}

private fun JsonNode.toPoint(fields: DataGoKrFieldNames): DataGoKrPoint? {
    val lat: Double = text(fields.lat)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val lng: Double = text(fields.lng)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    return DataGoKrPoint(
        lat = lat,
        lng = lng,
        name = text(fields.name),
        address = text(fields.address),
        sigungu = text(fields.sigungu),
        phone = text(fields.phone),
    )
}

private fun JsonNode.text(candidates: List<String>): String? =
    candidates.firstNotNullOfOrNull { field ->
        get(field)?.asText()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    }
