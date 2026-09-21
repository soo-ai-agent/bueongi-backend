package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import io.soo.bueongi.support.logging.ExceptionLogLine
import io.soo.bueongi.support.logging.IGNORED_EXCEPTION
import io.soo.bueongi.support.logging.causeChainOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

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

    private val cache = TmapRouteCache()

    private val objectMapper = ObjectMapper()

    fun fetchRoutes(
        origin: TmapCoord,
        dest: TmapCoord,
        destName: String? = null,
    ): List<TmapRoute> {
        if (!isEnabled) {
            return emptyList()
        }
        val routes: ArrayList<TmapRoute> = ArrayList()
        val seen: HashSet<Long> = HashSet()
        for (option in DEFAULT_SEARCH_OPTIONS) {
            val route: TmapRoute = call(origin, dest, destName, option, passList = null) ?: continue
            if (!seen.add(route.identityKey)) {
                continue
            }
            routes.add(route)
        }
        return routes
    }

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
            // Tmap 응답에는 NUL 이 섞여 와 문자열로 받아 다듬은 뒤 파싱한다.
            val raw: String? = restClient.post()
                .uri(PEDESTRIAN_ENDPOINT)
                .header("appKey", appKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body<String>()
            val json: JsonNode? = if (raw == null) {
                null
            } else {
                objectMapper.readTree(sanitizeTmapJson(raw))
            }
            val route: TmapRoute?
            if (json == null) {
                route = null
            } else {
                route = parseTmapGeoJson(json, searchOption)
            }
            if (route != null) {
                cache.put(origin, dest, searchOption, passList, route)
            }
            route
        } catch (e: Exception) {
            log.warn(
                ExceptionLogLine(
                    kind = IGNORED_EXCEPTION,
                    counterpart = PROVIDER,
                    subject = PEDESTRIAN_PATH,
                    cause = causeChainOf(e, appKey),
                    action = "이 후보 하나만 버림(나머지 옵션·폴백으로 계속)",
                    paging = "searchOption=$searchOption",
                ).format(),
            )
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

        private const val PEDESTRIAN_ENDPOINT: String = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"

        // 로그 대상 칸은 경로만 적는다 — 송신 줄과 같은 자리에서 같은 값이 보여야 한다.
        private const val PEDESTRIAN_PATH: String = "/tmap/routes/pedestrian"

        private const val PROVIDER: String = "tmap"

        // Tmap searchOption 규격: 추천=0, 대로우선=4, 최단=10.
        private val DEFAULT_SEARCH_OPTIONS: List<String> = listOf("0", "4", "10")

        private const val VIA_SEARCH_OPTION: String = "0"
    }
}
