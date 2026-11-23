package io.soo.bueongi.client.tmap.response

import com.fasterxml.jackson.databind.JsonNode
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

fun parseTmapGeoJson(root: JsonNode, searchOption: String): TmapRoute? {
    val features: JsonNode = root.path("features")
    if (!features.isArray) {
        return null
    }

    val path: ArrayList<TmapCoord> = ArrayList()
    val steps: ArrayList<NavStep> = ArrayList()
    var distanceMeters: Double = 0.0
    var timeSeconds: Int = 0

    for (feature in features) {
        val props: JsonNode = feature.path("properties")
        // 총거리·총시간은 첫 피처에만 실려 온다(실측).
        if (distanceMeters == 0.0) {
            distanceMeters = numberOf(props, "totalDistance")
        }
        if (timeSeconds == 0) {
            timeSeconds = numberOf(props, "totalTime").toInt()
        }

        val geom: JsonNode = feature.get("geometry") ?: continue
        when (geom.path("type").asText()) {
            "LineString" -> appendLineString(geom, props, path, steps)
            "Point" -> appendPoint(geom, props, steps)
        }
    }

    if (path.size < 2) {
        return null
    }
    if (distanceMeters == 0.0) {
        distanceMeters = pathLengthMeters(path)
    }
    steps.sortBy { it.index }
    return TmapRoute(
        searchOption = searchOption,
        path = path,
        distanceMeters = distanceMeters.roundToInt().toDouble(),
        timeSeconds = timeSeconds,
        steps = steps,
    )
}

private fun appendLineString(
    geom: JsonNode,
    props: JsonNode,
    path: MutableList<TmapCoord>,
    steps: MutableList<NavStep>,
) {
    val coords: JsonNode = geom.get("coordinates") ?: return
    for (coordNode in coords) {
        val coord: TmapCoord = parseLngLat(coordNode) ?: continue
        path.add(coord)
    }
    if (steps.isEmpty()) {
        return
    }
    val segDistance: Int = numberOf(props, "distance").toInt()
    val segTime: Int = numberOf(props, "time").toInt()
    if (segDistance <= 0 && segTime <= 0) {
        return
    }
    val lastIndex: Int = steps.size - 1
    steps[lastIndex] = steps[lastIndex].copy(distanceMeters = segDistance, timeSeconds = segTime)
}

// 구간 거리·시간은 뒤따르는 LineString 이 채운다.
private fun appendPoint(geom: JsonNode, props: JsonNode, steps: MutableList<NavStep>) {
    val pointCoords: JsonNode = geom.get("coordinates") ?: return
    val point: TmapCoord = parseLngLat(pointCoords) ?: return
    steps.add(
        NavStep(
            index = props.path("index").asInt(),
            lat = point.lat,
            lng = point.lng,
            description = props.path("description").asText(),
            turnType = props.path("turnType").asInt(),
            distanceMeters = 0,
            timeSeconds = 0,
            pointType = props.path("pointType").asText(),
        ),
    )
}

private fun numberOf(props: JsonNode, field: String): Double {
    val node: JsonNode = props.path(field)
    if (!node.isNumber) {
        return 0.0
    }
    return node.asDouble()
}

// GeoJSON 좌표는 [lng, lat] 순서다.
private fun parseLngLat(node: JsonNode): TmapCoord? {
    if (!node.isArray || node.size() < 2) {
        return null
    }
    val lng: Double = node.get(0).asDouble()
    val lat: Double = node.get(1).asDouble()
    if (!lat.isFinite() || lat !in -90.0..90.0) {
        return null
    }
    if (!lng.isFinite() || lng !in -180.0..180.0) {
        return null
    }
    return TmapCoord(lat, lng)
}

// 자립 모듈 원칙상 storage SpatialMath 를 참조하지 않는 의도적 내부 중복
private fun pathLengthMeters(path: List<TmapCoord>): Double {
    var sum: Double = 0.0
    for (i in 0 until path.size - 1) sum += haversineMeters(path[i], path[i + 1])
    return sum
}

private const val EARTH_RADIUS_M: Double = 6_371_000.0

private fun haversineMeters(a: TmapCoord, b: TmapCoord): Double {
    val dLat: Double = Math.toRadians(b.lat - a.lat)
    val dLng: Double = Math.toRadians(b.lng - a.lng)
    val h: Double =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
    return EARTH_RADIUS_M * 2 * atan2(sqrt(h), sqrt(1 - h))
}
