package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
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

    private val cache: TmapRouteCache = TmapRouteCache()

    private val objectMapper: ObjectMapper = ObjectMapper()

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
        for (option in TmapApiSpec.DEFAULT_SEARCH_OPTIONS) {
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
        return call(origin, dest, destName, TmapApiSpec.VIA_SEARCH_OPTION, passList.ifEmpty { null })
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
            // Tmap 응답에는 NUL 이 섞여 오므로 문자열로 받아 다듬은 뒤 파싱한다(sanitizeTmapJson 주석 참고).
            val raw: String? = restClient.post()
                .uri(TmapApiSpec.PEDESTRIAN_ENDPOINT)
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
                route = TmapGeoJson.parse(json, searchOption)
            }
            if (route != null) {
                cache.put(origin, dest, searchOption, passList, route)
            }
            route
        } catch (e: Exception) {
            // 시크릿(appKey) 비노출 — 스택 대신 원인 사슬만 남긴다.
            log.warn("Tmap 보행자 경로 호출 실패(option={}): {}", searchOption, causeChainOf(e))
            null
        }
    }

    /**
     * 예외 메시지만 남기면 진짜 원인이 사라진다 — 실제로 파싱 오류가 겉의
     * "Error while extracting response" 뒤에 가려 오래 안 보였다(NUL 사건, 2026-08-15).
     */
    private fun causeChainOf(e: Throwable): String {
        val chain: StringBuilder = StringBuilder()
        var current: Throwable? = e
        var depth: Int = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (depth > 0) {
                chain.append(" <- ")
            }
            chain.append(current.javaClass.simpleName).append(": ").append(current.message)
            if (current.cause === current) {
                return chain.toString()
            }
            current = current.cause
            depth++
        }
        return chain.toString()
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

        /** 원인 사슬 로깅 깊이 상한 — 순환·과다 로그 방지. */
        private const val MAX_CAUSE_DEPTH: Int = 5
    }
}
