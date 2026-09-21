package io.soo.bueongi.client.safemap

import io.soo.bueongi.support.logging.ExceptionLogLine
import io.soo.bueongi.support.logging.IGNORED_EXCEPTION
import io.soo.bueongi.support.logging.causeChainOf
import io.soo.bueongi.support.logging.maskSecrets
import io.soo.bueongi.support.util.Wgs84Point
import io.soo.bueongi.support.util.resolveKoreanWgs84OrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

// 인증키는 URL-인코딩분 그대로 써야 해 URI 를 문자열로 조립한다.
@Component
class SafemapPoliceClient(
    @Value("\${bueongi.safemap.service-key:}") serviceKey: String,
    @Value("\${bueongi.safemap.base-url:https://safemap.go.kr}") baseUrl: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(SafemapPoliceClient::class.java)
    private val serviceKey: String = serviceKey.trim()
    private val endpoint: String = "${baseUrl.trimEnd('/')}$PATH"
    private val restClient: RestClient = restClientBuilder.clone().build()

    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    // null = 수집 실패, 빈 목록과 다르다 — 호출자는 기존 데이터를 유지한다.
    fun fetchPoliceFacilities(): List<SafemapPolicePoint>? {
        check(isEnabled) { "생활안전지도 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points: ArrayList<SafemapPolicePoint> = ArrayList()
        var received: Long = 0L
        var totalCount: Long? = null

        for (pageNo in 1..MAX_PAGES) {
            val response: SafemapResponse = fetchPage(pageNo) ?: return null
            if (!isSuccess(response.header)) {
                return null
            }
            val rows: List<SafemapPoliceRow> = response.body?.items?.item ?: emptyList()
            if (response.body?.totalCount != null) {
                totalCount = response.body.totalCount
            }
            received += rows.size
            addPoints(rows, points)
            if (isLastPage(rows.size, received, totalCount)) {
                break
            }
        }
        return points
    }

    private fun fetchPage(pageNo: Int): SafemapResponse? {
        val url: String =
            "$endpoint?serviceKey=$serviceKey" +
                "&pageNo=$pageNo&numOfRows=$PAGE_SIZE&returnType=JSON"
        val response: SafemapResponse? =
            try {
                restClient.get().uri(URI.create(url)).retrieve().body(SafemapResponse::class.java)
            } catch (e: Exception) {
                log.warn(failureLine(cause = causeChainOf(e, serviceKey), pageNo = pageNo))
                return null
            }
        if (response == null) {
            log.warn(failureLine(cause = "빈 응답(본문 없음)", pageNo = pageNo))
        }
        return response
    }

    private fun isSuccess(header: SafemapResponseHeader?): Boolean {
        val code: String? = header?.resultCode
        if (code.isNullOrBlank() || code in SUCCESS_CODES) {
            return true
        }
        log.warn(
            failureLine(
                cause = "resultCode=$code(${maskSecrets(header.resultMsg.orEmpty())})",
                pageNo = 0,
            ),
        )
        return false
    }

    // 좌표를 0 으로 채워 보내는 행은 지도에 찍을 수 없어 버린다(실측).
    private fun addPoints(
        rows: List<SafemapPoliceRow>,
        into: MutableList<SafemapPolicePoint>,
    ) {
        for (row in rows) {
            val point: Wgs84Point = resolveKoreanWgs84OrNull(row.x, row.y) ?: continue
            into.add(
                SafemapPolicePoint(
                    lat = point.lat,
                    lng = point.lng,
                    name = row.name.valueOrNull(),
                    address = row.roadAddress.valueOrNull(),
                    phone = row.phone.valueOrNull(),
                    sigungu = row.sigungu.valueOrNull(),
                ),
            )
        }
    }

    // 서버가 numOfRows 보다 적게 줄 수 있어 건수만으로 끊지 않는다(실측).
    private fun isLastPage(
        rowCount: Int,
        received: Long,
        totalCount: Long?,
    ): Boolean {
        if (rowCount == 0) {
            return true
        }
        if (totalCount != null) {
            return received >= totalCount
        }
        return rowCount < PAGE_SIZE
    }

    private fun failureLine(
        cause: String,
        pageNo: Int,
    ): String =
        ExceptionLogLine(
            kind = IGNORED_EXCEPTION,
            counterpart = PROVIDER,
            subject = PATH,
            cause = cause,
            action = "수집 중단(기존 데이터 유지)",
            paging = if (pageNo > 0) "page=$pageNo" else "",
        ).format()

    private companion object {
        const val PATH: String = "/openapi2/IF_0036"

        // 송신 줄의 원천 이름과 같아야 세로로 짝이 보인다.
        const val PROVIDER: String = "safemap"

        const val PAGE_SIZE: Int = 1000

        const val MAX_PAGES: Int = 50

        val SUCCESS_CODES: Set<String> = setOf("0", "00")
    }
}

// 이 원천은 값이 없을 때 빈 칸이 아니라 `-` 를 적는다(실측).
private fun String?.valueOrNull(): String? {
    val text: String = this?.trim() ?: return null
    if (text.isEmpty() || text == "-" || text == "null") {
        return null
    }
    return text
}
