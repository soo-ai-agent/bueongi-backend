package io.soo.bueongi.client.safetydata.streetlamp

import io.soo.bueongi.support.logging.ExceptionLogLine
import io.soo.bueongi.support.logging.IGNORED_EXCEPTION
import io.soo.bueongi.support.logging.causeChainOf
import io.soo.bueongi.support.logging.maskSecrets
import io.soo.bueongi.support.util.Wgs84Point
import io.soo.bueongi.support.util.resolveKoreanWgs84OrNull
import java.net.URI
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** 재난안전데이터 공유플랫폼 가로등(DSSP-IF-00084). 인증키는 URL-인코딩분 그대로 써야 해 URI 를 문자열로 조립한다. */
@Component
class StreetLampClient(
    @Value("\${bueongi.safetydata.service-key:}") serviceKey: String,
    @Value("\${bueongi.safetydata.base-url:https://www.safetydata.go.kr}") baseUrl: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(StreetLampClient::class.java)
    private val serviceKey: String = serviceKey.trim()
    private val endpoint: String = "${baseUrl.trimEnd('/')}$PATH"
    private val restClient: RestClient = restClientBuilder.clone().build()

    val isEnabled: Boolean
        get() = serviceKey.isNotEmpty()

    /** 계약: null = 수집 실패 — 호출자는 기존 데이터를 유지한다. 빈 목록과 다르다. */
    fun fetchStreetLamps(): List<StreetLampPoint>? {
        check(isEnabled) { "재난안전데이터 인증키 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points: ArrayList<StreetLampPoint> = ArrayList()
        var received: Long = 0L
        var totalCount: Long? = null

        for (pageNo in 1..MAX_PAGES) {
            val response: StreetLampResponse = fetchPage(pageNo) ?: return null
            if (!isSuccess(response.header)) {
                return null
            }
            val rows: List<StreetLampRow> = response.body.orEmpty()
            if (response.totalCount != null) {
                totalCount = response.totalCount
            }
            received += rows.size
            addPoints(rows, points)
            if (isLastPage(rows.size, received, totalCount)) {
                break
            }
        }
        return points
    }

    private fun fetchPage(pageNo: Int): StreetLampResponse? {
        val url: String = "$endpoint?serviceKey=$serviceKey" +
            "&pageNo=$pageNo&numOfRows=$PAGE_SIZE&returnType=json"
        val response: StreetLampResponse? = try {
            restClient.get().uri(URI.create(url)).retrieve().body(StreetLampResponse::class.java)
        } catch (e: Exception) {
            log.warn(failureLine(cause = causeChainOf(e, serviceKey), pageNo = pageNo))
            return null
        }
        if (response == null) {
            log.warn(failureLine(cause = "빈 응답(본문 없음)", pageNo = pageNo))
        }
        return response
    }

    private fun isSuccess(header: StreetLampResponseHeader?): Boolean {
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

    private fun addPoints(rows: List<StreetLampRow>, into: MutableList<StreetLampPoint>) {
        for (row in rows) {
            val point: Wgs84Point = resolveKoreanWgs84OrNull(row.x, row.y) ?: continue
            into.add(
                StreetLampPoint(
                    lat = point.lat,
                    lng = point.lng,
                    address = row.roadAddress ?: row.lotAddress,
                    sigungu = row.sigungu,
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

    private fun failureLine(cause: String, pageNo: Int): String = ExceptionLogLine(
        kind = IGNORED_EXCEPTION,
        counterpart = PROVIDER,
        subject = PATH,
        cause = cause,
        action = "수집 중단(기존 데이터 유지)",
        paging = if (pageNo > 0) "page=$pageNo" else "",
    ).format()

    private companion object {
        const val PATH: String = "/V2/api/DSSP-IF-00084"

        /** 송신 줄의 원천 이름과 같아야 세로로 짝이 보인다. */
        const val PROVIDER: String = "safetydata"

        /** data.go.kr(100) 과 달리 1,000 을 받아 호출 수가 훨씬 적다. */
        const val PAGE_SIZE: Int = 1000

        /** 무한 루프 방지 — 원천 전량이 1,500건이라 두 자리면 충분하다. */
        const val MAX_PAGES: Int = 50

        val SUCCESS_CODES: Set<String> = setOf("0", "00")
    }
}
