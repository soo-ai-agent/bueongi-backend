package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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

    fun fetchRoute(
        origin: TmapCoord,
        destination: TmapCoord,
        destinationName: String,
        searchOption: String,
        passList: List<TmapCoord>,
    ): TmapRoute? {
        if (!isEnabled) {
            return null
        }
        val cached: TmapRoute? = cache.get(origin, destination, searchOption, passList)
        if (cached != null) {
            return cached
        }

        val route: TmapRoute? =
            try {
                val body: LinkedHashMap<String, String> =
                    linkedMapOf(
                        "startX" to origin.lng.toString(),
                        "startY" to origin.lat.toString(),
                        "endX" to destination.lng.toString(),
                        "endY" to destination.lat.toString(),
                        "startName" to "출발",
                        "endName" to destinationName,
                        "reqCoordType" to "WGS84GEO",
                        "resCoordType" to "WGS84GEO",
                        "searchOption" to searchOption,
                    )
                if (passList.isNotEmpty()) {
                    body["passList"] = passList.joinToString("_") { "${it.lng},${it.lat}" }
                }
                // Tmap 응답에는 NUL 이 섞여 와 문자열로 받아 다듬은 뒤 파싱한다.
                val raw: String? =
                    restClient.post()
                        .uri(PEDESTRIAN_ENDPOINT)
                        .header("appKey", appKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body<String>()
                val json: JsonNode = objectMapper.readTree(sanitizeTmapJson(raw.orEmpty()))
                parseTmapGeoJson(json, searchOption)
            } catch (e: Exception) {
                log.warn(
                    "$PROVIDER 호출 실패 | $PEDESTRIAN_PATH | searchOption=$searchOption | " +
                        "원인=${e.javaClass.simpleName} | 조치=이 후보 하나만 버림(나머지 옵션으로 계속)",
                )
                null
            }

        if (route != null) {
            cache.put(origin, destination, searchOption, passList, route)
        }
        return route
    }

    companion object {
        private const val PEDESTRIAN_ENDPOINT: String = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"

        // 로그 대상 칸은 경로만 적는다 — 송신 줄과 같은 자리에서 같은 값이 보여야 한다.
        private const val PEDESTRIAN_PATH: String = "/tmap/routes/pedestrian"

        private const val PROVIDER: String = "tmap"
    }
}
