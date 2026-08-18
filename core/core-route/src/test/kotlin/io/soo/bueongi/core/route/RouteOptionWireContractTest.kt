package io.soo.bueongi.core.route

import io.soo.bueongi.core.route.domain.model.DataProvenance
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.core.enums.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.model.RouteTagVariant
import io.soo.bueongi.core.enums.RouteType

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteOptionWireContractTest {

    private val mapper = ObjectMapper()

    private fun richOption() = RouteOption(
        id = "tmap-0-0",
        name = "안심 경로",
        time = "10분",
        dist = "700m",
        desc = "안전 점수 80점",
        tags = listOf(RouteTag("안심", RouteTagVariant.MINT)),
        type = RouteType.SAFE,
        path = listOf(GeoPoint(37.5, 127.0)),
        steps = listOf(NavStep(index = 0, lat = 37.5, lng = 127.0, description = "출발", turnType = 200, distanceM = 10, timeS = 8, pointType = "SP")),
        score = 80,
        markers = listOf(SafetySpotPoi(type = SafetySpotPoiType.CCTV, x = 50, y = 50, lat = 37.5, lng = 127.0)),
        breakdown = SafetyBreakdown(routeKm = 0.7, cctvDensity = 4.5, lampDensity = 0.0, bellDensity = 0.0, safehouseCount = 0, policeCount = 0, safePathOverlap = 0.0, crimeProneAreaCount = 0),
        provenance = DataProvenance(kind = "live", basedOn = "2026.06", origin = "공공데이터포털"),
    )

    @Test
    fun `RouteOption JSON 필드명은 프론트 계약 그대로다(path·steps 포함)`() {
        val json = mapper.readTree(mapper.writeValueAsString(richOption()))

        assertThat(json.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "id", "name", "time", "dist", "desc", "tags", "type",
            "path", "steps", "score", "markers", "breakdown", "provenance",
        )
        assertThat(json.at("/path/0").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder("lat", "lng")
        assertThat(json.at("/steps/0").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "index", "lat", "lng", "description", "turnType", "distanceM", "timeS", "pointType",
        )
        assertThat(json.at("/tags/0").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder("text", "variant")
        assertThat(json.at("/breakdown").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "routeKm", "cctvDensity", "lampDensity", "bellDensity", "safehouseCount", "policeCount", "safePathOverlap",
            "crimeProneAreaCount",
        )
        assertThat(json.at("/provenance").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder("kind", "basedOn", "origin")
    }

    @Test
    fun `enum 판별자는 기존 소문자 와이어 문자열로 직렬화된다`() {
        val json = mapper.readTree(mapper.writeValueAsString(richOption()))

        assertThat(json.get("type").asText()).isEqualTo("safe")
        assertThat(json.at("/tags/0/variant").asText()).isEqualTo("mint")
        assertThat(json.at("/markers/0/type").asText()).isEqualTo("cctv")
    }

    @Test
    fun `폴백 응답(확장 필드 null)은 NON_NULL 로 기존 shape 를 유지한다`() {
        val fallback = RouteOption(
            id = "1", name = "추천 경로", time = "7분", dist = "460m", desc = "d",
            tags = listOf(RouteTag("안심", RouteTagVariant.MINT)), type = RouteType.SAFE,
        )
        val json = mapper.readTree(mapper.writeValueAsString(fallback))
        assertThat(json.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "id", "name", "time", "dist", "desc", "tags", "type",
        )
    }

    @Test
    fun `대표 응답(폴백) 전체 JSON 스냅샷이 기존 계약과 동일하다`() {
        val fallback = RouteOption(
            id = "1", name = "추천 경로", time = "7분", dist = "460m", desc = "d",
            tags = listOf(RouteTag("안심", RouteTagVariant.MINT)), type = RouteType.SAFE,
        )
        val expected = mapper.readTree(
            """{"id":"1","name":"추천 경로","time":"7분","dist":"460m","desc":"d",""" +
                """"tags":[{"text":"안심","variant":"mint"}],"type":"safe"}""",
        )
        assertThat(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(fallback)).isEqualTo(expected)
    }
}
