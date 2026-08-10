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

object TmapGeoJson {

    fun parse(root: JsonNode, searchOption: String): TmapRoute? {
        val features: JsonNode = root.get("features") ?: return null
        if (!features.isArray) {
            return null
        }

        val path: ArrayList<TmapCoord> = ArrayList<TmapCoord>()
        val steps: ArrayList<NavStep> = ArrayList<NavStep>()
        var distanceM: Double = 0.0
        var timeS: Int = 0
        for (feature in features) {
            val props: JsonNode? = feature.get("properties")
            if (props != null) {
                val totalDistance: JsonNode? = props.get("totalDistance")
                if (distanceM == 0.0 && totalDistance != null && totalDistance.isNumber) {
                    distanceM = totalDistance.asDouble()
                }
                val totalTime: JsonNode? = props.get("totalTime")
                if (timeS == 0 && totalTime != null && totalTime.isNumber) {
                    timeS = totalTime.asInt()
                }
            }
            val geom: JsonNode = feature.get("geometry") ?: continue
            when (geom.get("type")?.asText()) {
                "LineString" -> {
                    val coords: JsonNode = geom.get("coordinates") ?: continue
                    for (coordNode in coords) parseLngLat(coordNode)?.let { path.add(it) }
                    if (steps.isNotEmpty() && props != null) {
                        val segDist: Int = props.get("distance")?.takeIf { it.isNumber }?.asInt() ?: 0
                        val segTime: Int = props.get("time")?.takeIf { it.isNumber }?.asInt() ?: 0
                        if (segDist > 0 || segTime > 0) {
                            val lastStepIndex: Int = steps.size - 1
                            steps[lastStepIndex] = steps[lastStepIndex].copy(distanceM = segDist, timeS = segTime)
                        }
                    }
                }
                "Point" -> {
                    val pointCoords: JsonNode = geom.get("coordinates") ?: continue
                    val point: TmapCoord = parseLngLat(pointCoords) ?: continue
                    steps.add(
                        NavStep(
                            index = props?.get("index")?.asInt() ?: 0,
                            lat = point.lat,
                            lng = point.lng,
                            description = props?.get("description")?.asText() ?: "",
                            turnType = props?.get("turnType")?.asInt() ?: 0,
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

    /** GeoJSON 좌표는 [lng, lat] 순서. */
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
        val h: Double = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}
