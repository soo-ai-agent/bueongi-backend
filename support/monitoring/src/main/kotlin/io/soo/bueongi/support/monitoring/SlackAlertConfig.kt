package io.soo.bueongi.support.monitoring

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Slack 알림 런타임 설정 홀더(단일 진실 원천) — 발송에 필요한 값을 [SlackAlertNotifier] 대신 여기 모아둔다.
 *
 * - **webhook URL 은 불변 시크릿**이다: env(`bueongi.alert.slack-webhook-url`)에서만 주입되고 편집 불가.
 *   원문은 [webhookUrl](발송 전용)로만 나가고, 조회 표면([maskedWebhook])에는 마스킹만 노출한다.
 * - **가변 설정**(발송 토글·스로틀·카테고리 뮤트)은 관리자가 [update] 로 바꾼다. 세 값 모두 `@Volatile` —
 *   발송 스레드가 항상 최신 값을 본다(단일 관리자 편집이라 필드 간 원자성까지는 요구하지 않는다).
 *
 * 생성자 하나: Spring 은 `@Value` 로 env 값을 주입하고, 테스트는 같은 생성자를 직접 호출해 값을 명시 주입한다
 * (직접 호출 시 `@Value` 는 무시된다). 두 값 모두 (String, Int) 라 별도 오버로드로 나눌 수 없어 단일 생성자로 둔다.
 */
@Component
class SlackAlertConfig(
    @Value("\${bueongi.alert.slack-webhook-url:}") webhookUrl: String,
    @Value("\${bueongi.alert.throttle-minutes:10}") throttleMinutes: Int,
) {

    /** webhook URL(시크릿, trim) — 편집 불가. 발송([webhookUrl])·설정 판정([webhookConfigured])에만 쓴다. */
    private val webhook: String = webhookUrl.trim()

    /** 마스터 발송 토글. 기본은 webhook 이 설정돼 있으면 on — 키만 넣으면 곧바로 발송되던 기존 동작 보존. */
    @Volatile
    private var masterEnabled: Boolean = webhook.isNotEmpty()

    /** 같은 알림 key 재발송 억제 창(분). 범위 밖 env 는 시작 시 보수적으로 절삭해 발송 로직을 보호한다. */
    @Volatile
    private var throttleMinutesValue: Int = throttleMinutes.coerceIn(MIN_THROTTLE_MINUTES, MAX_THROTTLE_MINUTES)

    /** 발송을 생략할 카테고리 key 집합(key 의 `:` 앞부분). 이력 기록은 생략하지 않는다. */
    @Volatile
    private var mutedCategorySet: Set<String> = emptySet()

    /** webhook 이 설정됐는가(효과 발송의 전제). */
    fun webhookConfigured(): Boolean = webhook.isNotEmpty()

    /** 마스터 발송 토글(관리자 편집값) — 효과 발송 여부가 아니라 토글 그 자체(요약 표시용). */
    fun enabled(): Boolean = masterEnabled

    /** 현재 스로틀 창(분). */
    fun throttleMinutes(): Int = throttleMinutesValue

    /** 현재 뮤트 카테고리 key 집합(불변 사본). */
    fun mutedCategories(): Set<String> = mutedCategorySet

    /** 효과 발송 여부 = webhook 설정됨 && 마스터 토글 on. */
    fun sendEnabled(): Boolean = webhookConfigured() && masterEnabled

    /** 스로틀 창(ms). */
    fun throttleMillis(): Long = throttleMinutesValue.toLong() * MILLIS_PER_MINUTE

    /**
     * 발송 전용 webhook 원문 — [SlackAlertNotifier] 만 호출한다. 응답·로그·예외 어디에도 노출 금지.
     */
    fun webhookUrl(): String = webhook

    /** 알림 key 의 카테고리(`:` 앞부분)가 뮤트 집합에 있으면 발송을 생략한다. */
    fun isMuted(key: String): Boolean {
        val category = key.substringBefore(DELIMITER)
        return mutedCategorySet.contains(category)
    }

    /**
     * 조회 표면용 마스킹 URL — 시크릿 원문은 절대 나가지 않는다.
     * 표준 slack webhook(services 경로형)이면 경로 토큰만 별표(T*** , B*** , 세 번째는 완전 마스킹),
     * 형식이 다르면 앞부분만 남기고 나머지를 별표한다. webhook 미설정이면 null.
     */
    fun maskedWebhook(): String? {
        if (webhook.isEmpty()) {
            return null
        }
        return maskWebhook(webhook)
    }

    /**
     * 가변 설정을 한 번에 교체한다. throttleMinutes 는 [MIN_THROTTLE_MINUTES]..[MAX_THROTTLE_MINUTES] 를
     * 벗어나면 거부한다(도달 불가 방어선 — 요청 DTO @Min/@Max 가 앞서 막지만, 이 홀더가 불변식의 최종 관문).
     * mutedCategories 의 값 규약(알려진 카테고리인지)은 도메인 서비스가 검증한다.
     */
    fun update(enabled: Boolean, throttleMinutes: Int, mutedCategories: Set<String>) {
        require(throttleMinutes in MIN_THROTTLE_MINUTES..MAX_THROTTLE_MINUTES) {
            "throttleMinutes 는 $MIN_THROTTLE_MINUTES..$MAX_THROTTLE_MINUTES 여야 합니다."
        }
        masterEnabled = enabled
        throttleMinutesValue = throttleMinutes
        mutedCategorySet = mutedCategories.toSet()
    }

    private fun maskWebhook(url: String): String {
        val markerIndex = url.indexOf(SERVICES_MARKER)
        if (markerIndex < 0) {
            return conservativeMask(url)
        }
        val prefix = url.take(markerIndex + SERVICES_MARKER.length)
        val segments = url.drop(markerIndex + SERVICES_MARKER.length)
            .split("/")
            .filter { it.isNotEmpty() }
        if (segments.isEmpty()) {
            return conservativeMask(url)
        }
        val maskedSegments = segments.mapIndexed { index, segment ->
            // 마지막(토큰) 세그먼트는 완전 마스킹, 앞 세그먼트는 유형 접두 1글자만 표기(T***/B***).
            if (index == segments.lastIndex) {
                MASK
            } else {
                segment.take(1) + MASK
            }
        }
        return prefix + maskedSegments.joinToString("/")
    }

    /** 형식 불명 URL — 앞 [CONSERVATIVE_PREFIX_LENGTH]자만 남기고 나머지를 별표(경로 시크릿 비노출). */
    private fun conservativeMask(url: String): String = url.take(CONSERVATIVE_PREFIX_LENGTH) + MASK

    companion object {
        /** 스로틀 창 허용 범위(분) — 요청 DTO 검증과 동일 경계. */
        const val MIN_THROTTLE_MINUTES = 1
        const val MAX_THROTTLE_MINUTES = 1440

        private const val MILLIS_PER_MINUTE = 60_000L
        private const val DELIMITER = ":"
        private const val SERVICES_MARKER = "/services/"
        private const val MASK = "***"
        private const val CONSERVATIVE_PREFIX_LENGTH = 8
    }
}
