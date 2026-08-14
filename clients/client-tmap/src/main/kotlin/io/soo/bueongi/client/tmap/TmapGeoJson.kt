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
            // 총거리·총시간은 첫 피처에만 실려 온다. 이미 받았으면 덮어쓰지 않는다.
            if (distanceM == 0.0) {
                distanceM = numberOf(props, "totalDistance") ?: 0.0
            }
            if (timeS == 0) {
                timeS = numberOf(props, "totalTime")?.toInt() ?: 0
            }

            val geom: JsonNode = feature.get("geometry") ?: continue
            when (geom.get("type")?.asText()) {
                "LineString" -> appendLineString(geom, props, path, steps)
                "Point" -> appendPoint(geom, props, steps)
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

    /** 선분 좌표를 경로에 잇고, 그 구간의 거리·시간을 직전 안내지점에 채운다. */
    private fun appendLineString(
        geom: JsonNode,
        props: JsonNode?,
        path: MutableList<TmapCoord>,
        steps: MutableList<NavStep>,
    ) {
        val coords: JsonNode = geom.get("coordinates") ?: return
        for (coordNode in coords) {
            val coord: TmapCoord = parseLngLat(coordNode) ?: continue
            path.add(coord)
        }
        if (steps.isEmpty() || props == null) {
            return
        }
        val segDistance: Int = numberOf(props, "distance")?.toInt() ?: 0
        val segTime: Int = numberOf(props, "time")?.toInt() ?: 0
        if (segDistance <= 0 && segTime <= 0) {
            return
        }
        val lastIndex: Int = steps.size - 1
        steps[lastIndex] = steps[lastIndex].copy(distanceM = segDistance, timeS = segTime)
    }

    /** 안내지점(회전·직진 등)을 추가한다. 구간 거리·시간은 뒤따르는 LineString 이 채운다. */
    private fun appendPoint(geom: JsonNode, props: JsonNode?, steps: MutableList<NavStep>) {
        val pointCoords: JsonNode = geom.get("coordinates") ?: return
        val point: TmapCoord = parseLngLat(pointCoords) ?: return
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

    private fun numberOf(props: JsonNode?, field: String): Double? {
        val node: JsonNode = props?.get(field) ?: return null
        if (!node.isNumber) {
            return null
        }
        return node.asDouble()
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
