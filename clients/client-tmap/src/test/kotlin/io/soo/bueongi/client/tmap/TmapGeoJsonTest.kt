package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TmapGeoJsonTest {
    private val mapper = ObjectMapper()

    private fun parse(json: String): TmapRoute? = parseTmapGeoJson(mapper.readTree(json), "0")

    @Test
    fun `Point 의 totalDistance 시간과 LineString 좌표를 추출`() {
        val json: String =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[127.0,37.5]},
               "properties":{"index":0,"totalDistance":880,"totalTime":700,"pointType":"SP",
                             "turnType":211,"description":"출발","distance":0,"time":0}},
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[127.0,37.5],[127.005,37.5],[127.01,37.5]]},
               "properties":{}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[127.01,37.5]},
               "properties":{"index":1,"pointType":"EP","turnType":212,"description":"도착","distance":0,"time":0}}
            ]}
            """.trimIndent()
        val route: TmapRoute? = parse(json)
        assertThat(route).isNotNull
        assertThat(route!!.distanceM).isEqualTo(880.0)
        assertThat(route.timeS).isEqualTo(700)
        assertThat(route.path).hasSize(3)
        assertThat(route.path.first()).isEqualTo(TmapCoord(37.5, 127.0))
        assertThat(route.steps).hasSize(2)
        assertThat(route.steps.first().description).isEqualTo("출발")
        assertThat(route.steps.map { it.index }).isSorted
    }

    @Test
    fun `totalDistance 없으면 좌표열 길이로 보정`() {
        val json: String =
            """
            {"features":[
              {"geometry":{"type":"LineString","coordinates":[[127.0,37.5],[127.01,37.5]]},"properties":{}}
            ]}
            """.trimIndent()
        val route: TmapRoute? = parse(json)
        assertThat(route).isNotNull
        assertThat(route!!.distanceM).isGreaterThan(800.0)
    }

    @Test
    fun `좌표가 2개 미만이면 null`() {
        val json: String = """{"features":[{"geometry":{"type":"LineString","coordinates":[[127.0,37.5]]},"properties":{}}]}"""
        assertThat(parse(json)).isNull()
    }

    @Test
    fun `features 가 없으면 null`() {
        assertThat(parse("""{"foo":1}""")).isNull()
    }

    @Test
    fun `비유효 좌표(범위 밖)는 경로에서 제외`() {
        val json: String =
            """
            {"features":[
              {"geometry":{"type":"LineString","coordinates":[[127.0,37.5],[999.0,37.5],[127.01,37.5]]},"properties":{}}
            ]}
            """.trimIndent()
        val route: TmapRoute? = parse(json)
        assertThat(route).isNotNull
        assertThat(route!!.path).hasSize(2)
    }
}
