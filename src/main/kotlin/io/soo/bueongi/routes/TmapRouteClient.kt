package io.soo.bueongi.routes

import com.fasterxml.jackson.databind.JsonNode
import io.soo.bueongi.facility.GeoPoint
import io.soo.bueongi.facility.SpatialMath
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * E-1 Tmap 보행자 경로 — 백엔드 서버사이드 직접 호출 클라이언트.
 *
 * `TMAP_APP_KEY`(application.yml: `bueongi.tmap.app-key`)가 설정되면 [isEnabled]=true 가 되어 실제 Tmap 을
 * 호출하고, 미설정이면 [SafeRouteService] 가 기존 [RouteService.compare] 로 폴백한다. 키는 `appKey` 헤더로만
 * 보내고 로그/응답에 절대 노출하지 않는다.
 *
 * 응답 GeoJSON 에서 LineString 좌표를 이어 경로 path 를, 첫 Point 의 totalDistance/totalTime 로 거리·시간을,
 * 각 Point 의 turnType/description 등으로 단계별 안내(steps)를 만든다. 네트워크/파싱 실패는 빈 결과(null)로
 * 안전 처리해 부분 실패를 무시하고, 전부 실패할 때만 상위에서 폴백한다.
 */
@Component
class TmapRouteClient(
    @Value("\${bueongi.tmap.app-key:}") appKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(TmapRouteClient::class.java)
    private val appKey: String = appKey.trim()

    val isEnabled: Boolean
        get() = appKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    // P0-2 쿼터 거버넌스: 동일 출발/도착/옵션/경유지 재요청은 TTL(10분) 동안 캐시를 재사용해
    // Tmap 호출(일 쿼터·요금)을 줄인다. 실패(null)는 캐시하지 않는다.
    private val cache = TmapRouteCache()

    /**
     * searchOption 별 후보 경로를 모은다(추천=0/대로우선=4/최단=10). 일부 옵션이 실패해도 성공한 후보만
     * 반환하고, 같은 경로(거리+좌표수 동일)는 중복 제거한다. 키 미설정이면 빈 목록.
     */
    fun fetchRoutes(
        origin: GeoPoint,
        dest: GeoPoint,
        destName: String? = null,
        searchOptions: List<String> = DEFAULT_SEARCH_OPTIONS,
    ): List<TmapRoute> {
        if (!isEnabled) return emptyList()
        val routes = ArrayList<TmapRoute>()
        val seen = HashSet<Long>()
        for (option in searchOptions) {
            val route = call(origin, dest, destName, option, passList = null) ?: continue
            val key = (route.distanceM.roundToLong() * 1000) + route.path.size
            if (!seen.add(key)) continue
            routes.add(route)
        }
        return routes
    }

    /** 경유지(passList)를 통과하는 단일 후보. 실패면 null. passList 가 비면 직선 호출과 같다. */
    fun fetchRouteVia(
        origin: GeoPoint,
        dest: GeoPoint,
        passList: List<GeoPoint>,
        destName: String? = null,
        searchOption: String = "0",
    ): TmapRoute? {
        if (!isEnabled) return null
        return call(origin, dest, destName, searchOption, passList.ifEmpty { null })
    }

    private fun call(
        origin: GeoPoint,
        dest: GeoPoint,
        destName: String?,
        searchOption: String,
        passList: List<GeoPoint>?,
    ): TmapRoute? {
        // 캐시 히트면 Tmap 호출 없이 재사용(동일 출발/도착 재요청 — P0-2 AC).
        cache.get(origin, dest, searchOption, passList)?.let { return it }
        return try {
            val body = buildRequestBody(origin, dest, destName, searchOption, passList)
            val json = restClient.post()
                .uri(PEDESTRIAN_ENDPOINT)
                .header("appKey", appKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode::class.java)
            json?.let { TmapGeoJson.parse(it, searchOption) }
                ?.also { cache.put(origin, dest, searchOption, passList, it) }
        } catch (e: Exception) {
            // 시크릿(appKey) 비노출 — 메시지만 로깅. 부분 실패는 무시(상위가 폴백 판단).
            log.warn("Tmap 보행자 경로 호출 실패(option={}): {}", searchOption, e.message)
            null
        }
    }

    private fun buildRequestBody(
        origin: GeoPoint,
        dest: GeoPoint,
        destName: String?,
        searchOption: String,
        passList: List<GeoPoint>?,
    ): Map<String, String> {
        val body = linkedMapOf(
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
        const val PEDESTRIAN_ENDPOINT = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"
        val DEFAULT_SEARCH_OPTIONS = listOf("0", "4", "10")
    }
}

/**
 * Tmap 보행자 경로 GeoJSON 파서(순수 함수). LineString 좌표를 이어 경로 path 를, 첫 Point 의
 * totalDistance/totalTime 으로 거리·시간을, 각 Point 의 turnType/description 등으로 단계별 안내(steps)를 만든다.
 * 네트워크와 분리해 단위 검증 가능하게 둔다.
 */
object TmapGeoJson {

    fun parse(root: JsonNode, searchOption: String): TmapRoute? {
        val features = root.get("features") ?: return null
        if (!features.isArray) return null

        val path = ArrayList<GeoPoint>()
        val steps = ArrayList<NavStep>()
        var distanceM = 0.0
        var timeS = 0
        for (feature in features) {
            val props = feature.get("properties")
            if (props != null) {
                if (distanceM == 0.0) props.get("totalDistance")?.takeIf { it.isNumber }?.let { distanceM = it.asDouble() }
                if (timeS == 0) props.get("totalTime")?.takeIf { it.isNumber }?.let { timeS = it.asInt() }
            }
            val geom = feature.get("geometry") ?: continue
            when (geom.get("type")?.asText()) {
                "LineString" -> {
                    val coords = geom.get("coordinates") ?: continue
                    for (c in coords) parseLngLat(c)?.let { path.add(it) }
                    // Tmap 구조상 거리/시간은 Point 가 아니라 그 뒤 LineString 의 properties 에 있다.
                    // 이 구간값을 직전 안내지점(Point) step 에 부여한다(= 그 지점→다음 지점 구간 거리/시간).
                    if (steps.isNotEmpty() && props != null) {
                        val segDist = props.get("distance")?.takeIf { it.isNumber }?.asInt() ?: 0
                        val segTime = props.get("time")?.takeIf { it.isNumber }?.asInt() ?: 0
                        if (segDist > 0 || segTime > 0) {
                            val i = steps.size - 1
                            steps[i] = steps[i].copy(distanceM = segDist, timeS = segTime)
                        }
                    }
                }
                "Point" -> {
                    val c = geom.get("coordinates") ?: continue
                    val pt = parseLngLat(c) ?: continue
                    steps.add(
                        NavStep(
                            index = props?.get("index")?.asInt() ?: 0,
                            lat = pt.lat,
                            lng = pt.lng,
                            description = props?.get("description")?.asText() ?: "",
                            turnType = props?.get("turnType")?.asInt() ?: 0,
                            // 거리/시간은 뒤따르는 LineString 구간에서 채운다(Point 자체엔 없음).
                            distanceM = 0,
                            timeS = 0,
                            pointType = props?.get("pointType")?.asText() ?: "",
                        ),
                    )
                }
            }
        }
        if (path.size < 2) return null
        if (distanceM == 0.0) distanceM = SpatialMath.pathLengthMeters(path)
        steps.sortBy { it.index }
        return TmapRoute(
            searchOption = searchOption,
            path = path,
            distanceM = distanceM.roundToInt().toDouble(),
            timeS = timeS,
            steps = steps,
        )
    }

    /** GeoJSON 좌표 [lng, lat] → GeoPoint. 비유효(NaN/∞/범위 밖)는 null. */
    private fun parseLngLat(node: JsonNode): GeoPoint? {
        if (!node.isArray || node.size() < 2) return null
        val lng = node.get(0).asDouble()
        val lat = node.get(1).asDouble()
        if (!lat.isFinite() || lat !in -90.0..90.0) return null
        if (!lng.isFinite() || lng !in -180.0..180.0) return null
        return GeoPoint(lat, lng)
    }
}

/**
 * Tmap 보행자 경로 1건. path 는 경로 라인 좌표열(안전 점수/마커의 원천), steps 는 단계별 안내(프론트 NavStep 의미).
 */
data class TmapRoute(
    val searchOption: String,
    val path: List<GeoPoint>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
)

/**
 * 단계별 안내 지점(좌회전/직진 등). 프론트 NavStep 과 동일 의미.
 * turnType: 11=직진 12=좌회전 13=우회전 14=U턴 125=육교 126=지하보도 211=출발 212=도착.
 */
data class NavStep(
    val index: Int,
    val lat: Double,
    val lng: Double,
    val description: String,
    val turnType: Int,
    val distanceM: Int,
    val timeS: Int,
    val pointType: String,
)
