package io.soo.bueongi.client.safe182

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient

// 일일 조회 한도(result=80)가 있어 전량 대신 사각 범위를 주고 부른다. 자료 출처 표기(경찰청)가 약관상 의무다.
@Component
class ChildSafeHouseClient(
    @Value("\${bueongi.safe182.esntl-id:}") esntlId: String,
    @Value("\${bueongi.safe182.auth-key:}") authKey: String,
    @Value("\${bueongi.safe182.base-url:https://www.safe182.go.kr}") baseUrl: String,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) {
    private val log: Logger = LoggerFactory.getLogger(ChildSafeHouseClient::class.java)
    private val esntlId: String = esntlId.trim()
    private val authKey: String = authKey.trim()
    private val endpoint: String = "${baseUrl.trimEnd('/')}$PATH"
    private val restClient: RestClient = restClientBuilder.clone().build()

    // 한쪽만 주면 원천이 result=99 로 거절한다(실측).
    val isEnabled: Boolean
        get() = esntlId.isNotEmpty() && authKey.isNotEmpty()

    // null = 수집 실패, 빈 목록과 다르다 — 호출자는 기존 데이터를 유지한다.
    fun fetchWithin(bounds: MapBounds): List<ChildSafeHousePoint>? {
        check(isEnabled) { "안전Dream 자격증명 미설정 — isEnabled 확인 후 호출해야 한다." }
        val points: ArrayList<ChildSafeHousePoint> = ArrayList()
        var received: Long = 0L
        var totalCount: Long? = null

        for (pageIndex in 1..MAX_PAGES) {
            val response: Safe182Response = fetchPage(pageIndex, bounds) ?: return null
            if (!isSuccess(response)) {
                return null
            }
            val rows: List<Safe182Row> = response.list
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

    // 원천이 비표준 `application/x-json` 으로 응답해 컨버터가 못 받는다 — 문자열로 받아 직접 파싱한다.
    private fun fetchPage(
        pageIndex: Int,
        bounds: MapBounds,
    ): Safe182Response? {
        return try {
            val body: String =
                restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formOf(pageIndex, bounds))
                    .retrieve()
                    .body(String::class.java)
                    ?: return null
            objectMapper.readValue(body, Safe182Response::class.java)
        } catch (e: Exception) {
            log.warn(failureLine(cause = e.javaClass.simpleName, pageIndex = pageIndex))
            null
        }
    }

    private fun formOf(
        pageIndex: Int,
        bounds: MapBounds,
    ): MultiValueMap<String, String> {
        val form: LinkedMultiValueMap<String, String> = LinkedMultiValueMap()
        form.add("esntlId", esntlId)
        form.add("authKey", authKey)
        form.add("pageIndex", pageIndex.toString())
        form.add("pageUnit", PAGE_SIZE.toString())
        form.add("clArray", CHILD_SAFE_HOUSE_CODE)
        form.add("minY", bounds.minLat.toString())
        form.add("minX", bounds.minLng.toString())
        form.add("maxY", bounds.maxLat.toString())
        form.add("maxX", bounds.maxLng.toString())
        return form
    }

    private fun isSuccess(response: Safe182Response): Boolean {
        val code: String? = response.result
        if (code == SUCCESS_CODE) {
            return true
        }
        if (code == QUOTA_EXCEEDED_CODE) {
            log.warn(
                failureLine(
                    cause = "일일 조회 한도 초과(result=$QUOTA_EXCEEDED_CODE)",
                    pageIndex = 0,
                    action = "오늘은 더 받지 않음(기존 데이터 유지)",
                ),
            )
            return false
        }
        log.warn(
            failureLine(
                cause = "result=$code",
                pageIndex = 0,
            ),
        )
        return false
    }

    private fun addPoints(
        rows: List<Safe182Row>,
        into: MutableList<ChildSafeHousePoint>,
    ) {
        for (row in rows) {
            val lat: Double = row.lcinfoLa ?: continue
            val lng: Double = row.lcinfoLo ?: continue
            if (!lat.isFinite() || !lng.isFinite()) {
                continue
            }
            into.add(
                ChildSafeHousePoint(
                    lat = lat,
                    lng = lng,
                    name = row.bsshNm,
                    address = row.adres,
                    phone = row.telno,
                ),
            )
        }
    }

    // 서버가 pageUnit 보다 적게 줄 수 있어 건수만으로 끊지 않는다(실측).
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
        pageIndex: Int,
        action: String = STOPPED_ACTION,
    ): String {
        val paging: String = if (pageIndex > 0) "page=$pageIndex" else ""
        return "$PROVIDER 수집 실패 | $PATH | $paging | 원인=$cause | 조치=$action"
    }

    private companion object {
        const val PATH: String = "/api/lcm/safeMap.do"

        // 송신 줄의 원천 이름과 같아야 세로로 짝이 보인다.
        const val PROVIDER: String = "safe182"

        const val STOPPED_ACTION: String = "수집 중단(기존 데이터 유지)"

        const val CHILD_SAFE_HOUSE_CODE: String = "09"

        // 원천이 정한 상한 — 더 크게 줘도 100 으로 자른다(실측).
        const val PAGE_SIZE: Int = 100

        const val MAX_PAGES: Int = 50

        const val SUCCESS_CODE: String = "00"
        const val QUOTA_EXCEEDED_CODE: String = "80"
    }
}
