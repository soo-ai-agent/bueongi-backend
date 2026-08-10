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
            // 시크릿(appKey) 비노출 — 메시지만 로깅
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

        /** Tmap searchOption 규격: 추천=0, 대로우선=4, 최단=10. */
        val DEFAULT_SEARCH_OPTIONS: List<String> = listOf("0", "4", "10")

        const val VIA_SEARCH_OPTION: String = "0"
    }
}
