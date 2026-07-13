package io.soo.bueongi.domain.admin

import io.soo.bueongi.support.error.InvalidSlackCategoryException
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import org.springframework.stereotype.Service

/**
 * 관리자 "Slack 설정 편집" 유스케이스 — 발송 토글·스로틀·카테고리 뮤트를 조회([view])/편집([update])하고,
 * 채널 상태를 테스트 발송([test])한다. webhook URL 은 편집 대상이 아니며(env 시크릿), 조회 시 마스킹만 노출한다.
 *
 * 알림 카테고리(고정 5종)와 라벨의 **단일 출처**가 여기다 — 저장소([SlackAlertConfig])는 뮤트된 key 집합만 알고,
 * 사람이 읽는 라벨과 "알려진 카테고리인지"의 값 규약은 이 도메인 서비스가 소유한다.
 */
@Service
class SlackConfigService(
    private val slackAlertConfig: SlackAlertConfig,
    private val alertNotifier: AlertNotifier,
) {

    /** 현재 설정을 요약으로 조회한다 — webhook 은 마스킹, 카테고리는 5종 라벨·뮤트 여부를 붙인다. */
    fun view(): SlackConfigSummary {
        val muted = slackAlertConfig.mutedCategories()
        val categories = KNOWN_CATEGORIES.map { (key, label) ->
            SlackCategoryConfig(key = key, label = label, muted = muted.contains(key))
        }
        return SlackConfigSummary(
            webhookConfigured = slackAlertConfig.webhookConfigured(),
            webhookMasked = slackAlertConfig.maskedWebhook(),
            enabled = slackAlertConfig.enabled(),
            throttleMinutes = slackAlertConfig.throttleMinutes(),
            categories = categories,
        )
    }

    /**
     * 편집 가능한 값(토글·스로틀·뮤트)을 교체하고 갱신된 요약을 돌려준다.
     * 알 수 없는 카테고리 key 는 [InvalidSlackCategoryException]→422 로 거부한다(값 규약은 도메인 소관).
     */
    fun update(update: SlackConfigUpdate): SlackConfigSummary {
        rejectUnknownCategories(update.mutedCategories)
        slackAlertConfig.update(
            enabled = update.enabled,
            throttleMinutes = update.throttleMinutes,
            mutedCategories = update.mutedCategories,
        )
        return view()
    }

    /** 테스트 발송을 시도하고 결과(성공 여부 + 현재 효과 발송 가능 여부)를 돌려준다. */
    fun test(): SlackTestResult {
        val sent = alertNotifier.sendTest()
        return SlackTestResult(sent = sent, enabled = slackAlertConfig.sendEnabled())
    }

    private fun rejectUnknownCategories(categories: Set<String>) {
        val unknown = categories - KNOWN_CATEGORIES.keys
        if (unknown.isNotEmpty()) {
            throw InvalidSlackCategoryException(
                rejectedValue = unknown.sorted(),
                knownKeys = KNOWN_CATEGORIES.keys.toList(),
            )
        }
    }

    companion object {
        /**
         * 알림 카테고리(고정 5종, 표시 순서 고정) — key(접두사) → 사람이 읽는 라벨. 뮤트/검증의 단일 출처.
         * key 는 실제 알림 key 의 `:` 앞부분과 일치한다(`api-500:<예외>`·`facility-refresh:<cdnType>` 등).
         */
        val KNOWN_CATEGORIES: Map<String, String> = linkedMapOf(
            "api-500" to "서버 오류(미처리 500)",
            "kakao-place-fallback" to "장소검색 폴백(Kakao)",
            "tmap-route-fallback" to "경로 폴백(Tmap)",
            "facility-index-empty" to "안심시설 데이터 없음",
            "facility-refresh" to "시설 라이브 갱신 실패",
        )
    }
}

/**
 * Slack 설정 편집 입력(도메인 값) — 웹 요청 DTO(@Valid 통과)에서 변환된 비null 값만 담는다.
 * 컨트롤러가 요청을 이 값으로 변환해 넘기므로 도메인 서비스는 웹 DTO 를 모른다(의존 방향 준수).
 */
data class SlackConfigUpdate(
    val enabled: Boolean,
    val throttleMinutes: Int,
    val mutedCategories: Set<String>,
)

/** GET/PUT /api/admin/slack-config 응답 — 현재 Slack 알림 설정 요약. */
data class SlackConfigSummary(
    /** webhook 이 env 로 설정됐는가(효과 발송의 전제). */
    val webhookConfigured: Boolean,
    /** 마스킹된 webhook(services 경로 토큰을 T*** , B*** , *** 로 별표). 미설정이면 null. 원문은 절대 노출 안 함. */
    val webhookMasked: String?,
    /** 마스터 발송 토글(효과 발송 = webhookConfigured && enabled). */
    val enabled: Boolean,
    /** 같은 알림 key 재발송 억제 창(분). */
    val throttleMinutes: Int,
    /** 알림 카테고리 5종(고정)과 뮤트 여부. */
    val categories: List<SlackCategoryConfig>,
)

/** 알림 카테고리 1건 — key(접두사)·라벨·뮤트 여부. */
data class SlackCategoryConfig(
    val key: String,
    val label: String,
    val muted: Boolean,
)

/** POST /api/admin/slack-config/test 응답 — 테스트 발송 성공 여부와 현재 효과 발송 가능 여부. */
data class SlackTestResult(
    val sent: Boolean,
    val enabled: Boolean,
)
