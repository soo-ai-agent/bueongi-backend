package io.soo.bueongi.core.route.domain.safety

import io.soo.bueongi.core.route.domain.safety.model.SafetyWeights
import io.soo.bueongi.core.route.domain.safety.service.SafetyWeightsCatalog
import io.soo.bueongi.support.util.SERVICE_ZONE
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SafetyWeightsCatalogTest {

    private val catalog = SafetyWeightsCatalog()

    @Test
    fun `밤낮 가중치 합은 둘 다 정확히 1`() {
        assertThat(catalog.weightsAt(seoul("2026-01-15T23:00")).sum).isCloseTo(1.0, within(1e-9))
        assertThat(catalog.weightsAt(seoul("2026-01-15T12:00")).sum).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `조명 가중치는 밤이 낮보다 무겁고 나머지는 반대다`() {
        val night: SafetyWeights = catalog.weightsAt(seoul("2026-06-15T23:00"))
        val day: SafetyWeights = catalog.weightsAt(seoul("2026-06-15T12:00"))

        assertThat(night.lamp).isGreaterThan(day.lamp)
        assertThat(day.cctv).isGreaterThan(night.cctv)
        assertThat(day.bell).isGreaterThan(night.bell)
        assertThat(day.police).isGreaterThan(night.police)
    }

    @Test
    fun `같은 18시 30분도 여름은 낮 겨울은 밤이다`() {
        assertThat(catalog.isNight(seoul("2026-06-15T18:30"))).isFalse()
        assertThat(catalog.isNight(seoul("2026-12-15T18:30"))).isTrue()
    }

    @Test
    fun `이른 아침도 계절에 따라 갈린다`() {
        assertThat(catalog.isNight(seoul("2026-06-15T05:30"))).isFalse()
        assertThat(catalog.isNight(seoul("2026-12-15T05:30"))).isTrue()
    }

    @Test
    fun `일몰 직전은 낮 직후는 밤`() {
        assertThat(catalog.isNight(seoul("2026-08-15T19:23"))).isFalse()
        assertThat(catalog.isNight(seoul("2026-08-15T19:25"))).isTrue()
    }

    @Test
    fun `시간대가 달라도 같은 순간이면 같은 판정`() {
        val seoulMidnight: ZonedDateTime = seoul("2026-03-15T00:30")
        val sameMomentInUtc: ZonedDateTime = seoulMidnight.withZoneSameInstant(ZoneOffset.UTC)

        assertThat(catalog.isNight(sameMomentInUtc)).isEqualTo(catalog.isNight(seoulMidnight))
        assertThat(catalog.isNight(sameMomentInUtc)).isTrue()
    }

    private fun seoul(localDateTime: String): ZonedDateTime =
        LocalDateTime.parse(localDateTime).atZone(SERVICE_ZONE)
}
