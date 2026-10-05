package io.soo.bueongi.core.route.domain.safety.service

import io.soo.bueongi.core.route.domain.safety.model.SafetyWeights
import io.soo.bueongi.support.util.SERVICE_ZONE
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.ZonedDateTime

@Component
class SafetyWeightsCatalog {
    fun weightsAt(time: ZonedDateTime): SafetyWeights {
        if (isNight(time)) {
            return NIGHT
        }
        return DAY
    }

    fun isNight(time: ZonedDateTime): Boolean {
        val seoul: ZonedDateTime = time.withZoneSameInstant(SERVICE_ZONE)
        val monthIndex: Int = seoul.monthValue - 1
        val clockTime: LocalTime = seoul.toLocalTime()
        return clockTime < SUNRISE_BY_MONTH[monthIndex] || clockTime >= SUNSET_BY_MONTH[monthIndex]
    }

    private companion object {
        val NIGHT = SafetyWeights(cctv = 0.14, lamp = 0.25, bell = 0.23, safehouse = 0.03, police = 0.07, safePath = 0.28)
        val DAY = SafetyWeights(cctv = 0.25, lamp = 0.07, bell = 0.18, safehouse = 0.08, police = 0.12, safePath = 0.30)

        // 서울 기준 월 중순 실측 일출 시각(KST, 오차 ±15분).
        val SUNRISE_BY_MONTH: List<LocalTime> =
            listOf(
                LocalTime.of(7, 46),
                LocalTime.of(7, 20),
                LocalTime.of(6, 41),
                LocalTime.of(5, 56),
                LocalTime.of(5, 23),
                LocalTime.of(5, 11),
                LocalTime.of(5, 24),
                LocalTime.of(5, 48),
                LocalTime.of(6, 13),
                LocalTime.of(6, 38),
                LocalTime.of(7, 8),
                LocalTime.of(7, 39),
            )

        // 서울 기준 월 중순 실측 일몰 시각(KST, 오차 ±15분).
        val SUNSET_BY_MONTH: List<LocalTime> =
            listOf(
                LocalTime.of(17, 33),
                LocalTime.of(18, 4),
                LocalTime.of(18, 32),
                LocalTime.of(18, 59),
                LocalTime.of(19, 25),
                LocalTime.of(19, 50),
                LocalTime.of(19, 51),
                LocalTime.of(19, 24),
                LocalTime.of(18, 43),
                LocalTime.of(17, 59),
                LocalTime.of(17, 24),
                LocalTime.of(17, 15),
            )
    }
}
