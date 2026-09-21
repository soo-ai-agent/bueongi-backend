package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

// Tmap 실호출 응답 10,520바이트에 NUL 3개(name·description·nearPoiName)가 들어 있었다.
class TmapResponseTextTest {
    private val nul: String = "\u0000"

    private val mapper = ObjectMapper()

    @Test
    fun `NUL 앞뒤가 같은 말이면 하나만 남긴다`() {
        val body = """{"name":"충림빌딩${nul}충림빌딩"}"""

        val json = mapper.readTree(sanitizeTmapJson(body))

        assertThat(json["name"].asText()).isEqualTo("충림빌딩")
    }

    @Test
    fun `뒤쪽이 더 길면 뒤쪽을 남긴다 - 안내 문구가 잘리지 않게`() {
        val body = """{"description":"충림빌딩${nul}충림빌딩에서 횡단보도 후 9m 이동"}"""

        val json = mapper.readTree(sanitizeTmapJson(body))

        assertThat(json["description"].asText()).isEqualTo("충림빌딩에서 횡단보도 후 9m 이동")
    }

    @Test
    fun `앞뒤가 다른 말이면 공백으로 잇는다`() {
        val body = """{"name":"가나${nul}다라"}"""

        val json = mapper.readTree(sanitizeTmapJson(body))

        assertThat(json["name"].asText()).isEqualTo("가나 다라")
    }

    @Test
    fun `NUL 이 없으면 원문 그대로다`() {
        val body = """{"name":"강남역","totalDistance":738}"""

        assertThat(sanitizeTmapJson(body)).isEqualTo(body)
    }
}
