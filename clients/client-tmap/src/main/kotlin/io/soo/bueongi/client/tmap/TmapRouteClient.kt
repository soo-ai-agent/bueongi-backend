package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * E-1 Tmap 보행자 경로 — 백엔드 서버사이드 직접 호출 클라이언트(자립 모듈).
 *
 * `TMAP_APP_KEY`(client-tmap.yml: `bueongi.tmap.app-key`)가 설정되면 [isEnabled]=true 가 되어 실제 Tmap 을
 * 호출하고, 미설정이면 상위(core-api SafeRouteService)가 결정론적 폴백으로 동작한다. 키는 `appKey` 헤더로만
 * 보내고 로그/응답에 절대 노출하지 않는다.
 *
 * 반환 모델은 이 모듈 소유의 [TmapRoute]/[TmapCoord]/[NavStep] — 도메인(GeoPoint) 매핑은 상위가 한다
 * (starter 의 client-example 이 자체 ClientResult 모델을 반환하는 것과 동일 규약).
 *
 * 네트워크/파싱 실패는 빈 결과(null)로 안전 처리해 부분 실패를 무시하고, 전부 실패할 때만 상위에서 폴백한다.
 */
@Component
class TmapRouteClient(
    @Value("\${bueongi.tmap.app-key:}") appKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log: Logger = LoggerFactory.getLogger(TmapRouteClient::class.java)
    private val appKey: String = appKey.trim()

    val isEnabled: Boolean
        get() = appKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    private val cache: TmapRouteCache = TmapRouteCache()

    /**
     * searchOption 별 후보 경로를 모은다(추천=0/대로우선=4/최단=10). 일부 옵션이 실패해도 성공한 후보만
     * 반환하고, 같은 경로([TmapRoute.identityKey] 동일)는 중복 제거한다. 키 미설정이면 빈 목록.
     */
    fun fetchRoutes(
        origin: TmapCoord,
        dest: TmapCoord,
        destName: String? = null,
    ): List<TmapRoute> {
        if (!isEnabled) {
            return emptyList()
        }
        val routes: ArrayList<TmapRoute> = ArrayList<TmapRoute>()
        val seen: HashSet<Long> = HashSet<Long>()
        for (option in DEFAULT_SEARCH_OPTIONS) {
            val route: TmapRoute = call(origin, dest, destName, option, passList = null) ?: continue
            if (!seen.add(route.identityKey)) {
                continue
            }
            routes.add(route)
        }
        return routes
    }

    /** 경유지(passList)를 통과하는 단일 후보(추천 옵션 고정). 실패면 null. passList 가 비면 직선 호출과 같다. */
    fun fetchRouteVia(
        origin: TmapCoord,
        dest: TmapCoord,
        passList: List<TmapCoord>,
        destName: String? = null,
    ): TmapRoute? {
        if (!isEnabled) {
            return null
        }
        return call(origin, dest, destName, VIA_SEARCH_OPTION, passList.ifEmpty { null })
    }

    private fun call(
        origin: TmapCoord,
        dest: TmapCoord,
        destName: String?,
        searchOption: String,
        passList: List<TmapCoord>?,
    ): TmapRoute? {
        cache.get(origin, dest, searchOption, passList)?.let { return it }
        return try {
            val body: Map<String, String> = buildRequestBody(origin, dest, destName, searchOption, passList)
            val json: JsonNode? = restClient.post()
                .uri(PEDESTRIAN_ENDPOINT)
                .header("appKey", appKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode::class.java)
            val route: TmapRoute? = json?.let { TmapGeoJson.parse(it, searchOption) }
            if (route != null) {
                cache.put(origin, dest, searchOption, passList, route)
            }
            route
        } catch (e: Exception) {
            // 시크릿(appKey) 비노출 — 메시지만 로깅. 부분 실패는 무시(상위가 폴백 판단).
            log.warn("Tmap 보행자 경로 호출 실패(option={}): {}", searchOption, e.message)
            null
        }
    }

    private fun buildRequestBody(
        origin: TmapCoord,
        dest: TmapCoord,
        destName: String?,
        searchOption: String,
        passList: List<TmapCoord>?,
    ): Map<String, String> {
        val body: LinkedHashMap<String, String> = linkedMapOf(
            "startX" to origin.lng.toString(),
            "startY" to origin.lat.toString(),
            "endX" to dest.lng.toString(),
            "endY" to dest.lat.toString(),
            "startName" to "출발",
            "endName" to (destName?.trim()?.takeIf { it.isNotEmpty() } ?: "목적지"),
            "reqCoordType" to "WGS84GEO",
            "resCoordType" to "WGS84GEO",
            "searchOption" to searchOption,
        )
        if (!passList.isNullOrEmpty()) {
            body["passList"] = passList.joinToString("_") { "${it.lng},${it.lat}" }
        }
        return body
    }

    companion object {
        const val PEDESTRIAN_ENDPOINT: String = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"

        /** 기본 후보 수집 옵션(추천=0/대로우선=4/최단=10) — 실제 요구가 생기기 전까지 조합은 고정한다. */
        val DEFAULT_SEARCH_OPTIONS: List<String> = listOf("0", "4", "10")

        /** 경유지(passList) 호출의 탐색 옵션 — 추천(0) 고정(경유지 자체가 우회를 표현하므로 옵션 조합 불필요). */
        const val VIA_SEARCH_OPTION: String = "0"
    }
}
