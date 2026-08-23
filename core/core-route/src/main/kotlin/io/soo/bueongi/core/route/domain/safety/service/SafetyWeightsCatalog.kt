package io.soo.bueongi.core.route.domain.safety.service

import io.soo.bueongi.core.route.domain.safety.model.SafetyWeights
import io.soo.bueongi.support.util.SERVICE_ZONE
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 그 시각에 쓸 항목별 가중치를 답한다.
 *
 * 안심귀가 앱인데 점수가 낮과 밤에 똑같았다. 조명은 해가 진 뒤에만 의미가 있다 —
 * 낮에 가로등이 많다고 더 안전한 길은 아니다. 그래서 낮 가중치는 밤에서 조명만 0.31 -> 0.10 으로
 * 줄이고, 줄인 몫을 시각과 무관하게 작동하는 나머지 축에 비율 그대로 나눠 준 값이다.
 */
@Component
class SafetyWeightsCatalog {

    fun weightsAt(time: ZonedDateTime): SafetyWeights {
        if (isNight(time)) {
            return NIGHT
        }
        return DAY
    }

    /** 해가 진 뒤부터 다음 날 해가 뜨기 전까지가 밤이다. */
    fun isNight(time: ZonedDateTime): Boolean {
        val seoul: ZonedDateTime = time.withZoneSameInstant(SERVICE_ZONE)
        val monthIndex: Int = seoul.monthValue - 1
        val clockTime: LocalTime = seoul.toLocalTime()
        return clockTime < SUNRISE_BY_MONTH[monthIndex] || clockTime >= SUNSET_BY_MONTH[monthIndex]
    }

    private companion object {

        /**
         * 조명이 최대 축이다. 비상벨은 위급할 때 도움을 부르는 유일한 축이라 밤낮 모두 무겁게 둔다.
         * 안심귀갓길(safePath)은 전국 원천이 2건뿐이라 실측 16개 후보에서 겹침이 전부 0.000 이었다 —
         * 0.15 를 들고 있어 봐야 점수 폭만 좁히므로 0.03 으로 내렸다. 없애지 않은 이유는
         * 데이터가 채워지면 계산·테스트가 그대로 되살아나야 하기 때문이다.
         */
        val NIGHT = SafetyWeights(cctv = 0.26, lamp = 0.31, bell = 0.19, safehouse = 0.09, police = 0.12, safePath = 0.03)

        /** 조명만 0.10 으로 줄이고 나머지를 비율대로 키운 값(0.90 / 0.69 = 1.30 배). */
        val DAY = SafetyWeights(cctv = 0.35, lamp = 0.10, bell = 0.25, safehouse = 0.12, police = 0.15, safePath = 0.03)

        /**
         * 서울 기준 월 중순 일출 시각(KST).
         *
         * 고정 시각(18~06시)으로 자르면 6월 일몰(19:50)과 12월 일몰(17:15)의 2시간 35분 차이를
         * 통째로 틀린다 — 6월 18시 30분은 훤한 대낮인데 밤으로 읽힌다. 그렇다고 일몰 계산식을
         * 넣을 만큼 정밀할 필요는 없어서, 월 중순 실제 값을 표로 둔다(오차 ±15분).
         * 새 외부 API 는 쓰지 않는다.
         */
        val SUNRISE_BY_MONTH: List<LocalTime> = listOf(
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

        /** 서울 기준 월 중순 일몰 시각(KST). */
        val SUNSET_BY_MONTH: List<LocalTime> = listOf(
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
