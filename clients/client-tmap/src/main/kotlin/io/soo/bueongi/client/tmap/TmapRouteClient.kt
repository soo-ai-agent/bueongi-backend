package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.databind.JsonNode
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
    private val log = LoggerFactory.getLogger(TmapRouteClient::class.java)
    private val appKey: String = appKey.trim()

    val isEnabled: Boolean
        get() = appKey.isNotEmpty()

    // connect/read 타임아웃은 전역 설정(application.yml: spring.http.client.*)이 이 빌더에 적용된다.
    private val restClient: RestClient = restClientBuilder.clone().build()

    // P0-2 쿼터 거버넌스: 동일 출발/도착/옵션/경유지 재요청은 TTL(10분) 동안 캐시를 재사용해
    // Tmap 호출(일 쿼터·요금)을 줄인다. 실패(null)는 캐시하지 않는다.
    private val cache = TmapRouteCache()

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
        val routes = ArrayList<TmapRoute>()
        val seen = HashSet<Long>()
        for (option in DEFAULT_SEARCH_OPTIONS) {
            val route = call(origin, dest, destName, option, passList = null) ?: continue
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
            val route = json?.let { TmapGeoJson.parse(it, searchOption) }
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

        /** 기본 후보 수집 옵션(추천=0/대로우선=4/최단=10) — 실제 요구가 생기기 전까지 조합은 고정한다. */
        val DEFAULT_SEARCH_OPTIONS = listOf("0", "4", "10")

        /** 경유지(passList) 호출의 탐색 옵션 — 추천(0) 고정(경유지 자체가 우회를 표현하므로 옵션 조합 불필요). */
        const val VIA_SEARCH_OPTION = "0"
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
        if (!features.isArray) {
            return null
        }

        val path = ArrayList<TmapCoord>()
        val steps = ArrayList<NavStep>()
        var distanceM = 0.0
        var timeS = 0
        for (feature in features) {
            val props = feature.get("properties")
            if (props != null) {
                // 총 거리/시간은 첫 유효값만 채택한다(첫 Point 의 totalDistance/totalTime).
                val totalDistance = props.get("totalDistance")
                if (distanceM == 0.0 && totalDistance != null && totalDistance.isNumber) {
                    distanceM = totalDistance.asDouble()
                }
                val totalTime = props.get("totalTime")
                if (timeS == 0 && totalTime != null && totalTime.isNumber) {
                    timeS = totalTime.asInt()
                }
            }
            val geom = feature.get("geometry") ?: continue
            when (geom.get("type")?.asText()) {
                "LineString" -> {
                    val coords = geom.get("coordinates") ?: continue
                    for (coordNode in coords) parseLngLat(coordNode)?.let { path.add(it) }
                    // Tmap 구조상 거리/시간은 Point 가 아니라 그 뒤 LineString 의 properties 에 있다.
                    // 이 구간값을 직전 안내지점(Point) step 에 부여한다(= 그 지점→다음 지점 구간 거리/시간).
                    if (steps.isNotEmpty() && props != null) {
                        val segDist = props.get("distance")?.takeIf { it.isNumber }?.asInt() ?: 0
                        val segTime = props.get("time")?.takeIf { it.isNumber }?.asInt() ?: 0
                        if (segDist > 0 || segTime > 0) {
                            val lastStepIndex = steps.size - 1
                            steps[lastStepIndex] = steps[lastStepIndex].copy(distanceM = segDist, timeS = segTime)
                        }
                    }
                }
                "Point" -> {
                    val pointCoords = geom.get("coordinates") ?: continue
                    val point = parseLngLat(pointCoords) ?: continue
                    steps.add(
                        NavStep(
                            index = props?.get("index")?.asInt() ?: 0,
                            lat = point.lat,
                            lng = point.lng,
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
        if (path.size < 2) {
            return null
        }
        if (distanceM == 0.0) {
            distanceM = pathLengthMeters(path)
        }
        steps.sortBy { it.index }
        return TmapRoute(
            searchOption = searchOption,
            path = path,
            distanceM = distanceM.roundToInt().toDouble(),
            timeS = timeS,
            steps = steps,
        )
    }

    /** GeoJSON 좌표 [lng, lat] → TmapCoord. 비유효(NaN/∞/범위 밖)는 null. */
    private fun parseLngLat(node: JsonNode): TmapCoord? {
        if (!node.isArray || node.size() < 2) {
            return null
        }
        val lng = node.get(0).asDouble()
        val lat = node.get(1).asDouble()
        if (!lat.isFinite() || lat !in -90.0..90.0) {
            return null
        }
        if (!lng.isFinite() || lng !in -180.0..180.0) {
            return null
        }
        return TmapCoord(lat, lng)
    }

    /**
     * totalDistance 결측 응답의 폴백용 경로 길이(하버사인 합). 자립 모듈 원칙(다른 모듈 미의존)을 위한
     * 클라이언트 내부 구현 — 도메인 공용 공간 연산의 단일 출처는 storage 의 SpatialMath 다.
     */
    private fun pathLengthMeters(path: List<TmapCoord>): Double {
        var sum = 0.0
        for (i in 0 until path.size - 1) sum += haversineMeters(path[i], path[i + 1])
        return sum
    }

    private const val EARTH_RADIUS_M = 6_371_000.0

    private fun haversineMeters(a: TmapCoord, b: TmapCoord): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}

/** Tmap 좌표 1점(WGS84). 자립 모듈 소유 — 도메인 GeoPoint 매핑은 상위가 한다. */
data class TmapCoord(val lat: Double, val lng: Double)

/**
 * Tmap 보행자 경로 1건. path 는 경로 라인 좌표열(안전 점수/마커의 원천), steps 는 단계별 안내(프론트 NavStep 의미).
 */
data class TmapRoute(
    val searchOption: String,
    val path: List<TmapCoord>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
) {
    /**
     * 같은 경로 판정 키(거리 + 좌표수). 옵션만 다르고 실제로 같은 길인 후보의 중복 제거에 쓴다 —
     * 클라이언트 수집([TmapRouteClient.fetchRoutes])과 도메인 후보 합류(SafeRouteService)가
     * 동일 판정을 공유하는 단일 출처.
     *
     * 거리(m)를 [PATH_SIZE_SLOT] 배로 밀어 하위 자리에 좌표수를 담는다 — 좌표수가 슬롯(1000) 미만이라는
     * 전제이며, 초과 시 거리 슬롯과 겹쳐 다른 경로가 같은 키를 가질 수 있다(도보 경로에선 충분한 여유).
     */
    val identityKey: Long
        get() = (distanceM.roundToLong() * PATH_SIZE_SLOT) + path.size

    companion object {
        /** identityKey 의 좌표수 슬롯 크기 — 거리와 좌표수를 한 Long 에 패킹하는 자릿수 기준. */
        const val PATH_SIZE_SLOT = 1_000L
    }
}

/**
 * 단계별 안내 지점(좌회전/직진 등). 프론트 NavStep 과 동일 의미.
 * turnType: 11=직진 12=좌회전 13=우회전 14=U턴 125=육교 126=지하보도 211=출발 212=도착.
 * 주의: 상위(core-api)의 RouteOption.steps 가 이 타입을 그대로 직렬화한다 — 프로퍼티명 전부가
 * 프론트 응답 계약이므로 리네임 금지(계약 고정 테스트: core-api RouteOptionWireContractTest).
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
