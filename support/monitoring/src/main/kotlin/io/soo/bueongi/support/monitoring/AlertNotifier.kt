package io.soo.bueongi.support.monitoring

/**
 * 운영 알림 발송 계약 — 외부 API 실패·서비스 이상을 사람 채널(Slack 등)로 알린다.
 *
 * 호출 규약:
 * - [notify] 는 **절대 예외를 던지지 않고, 호출 스레드를 막지 않는다**(비동기) — 알림 실패가
 *   서비스 요청 처리에 전이되면 안 된다.
 * - [key] 는 스로틀 단위다. 같은 key 는 스로틀 창(기본 10분) 안에서 1회만 발송된다 —
 *   외부 API 장애 시 요청마다 알림이 폭주하는 것을 막는다.
 * - 비활성([isEnabled]=false, webhook 미설정)이면 조용히 무시한다(로그만).
 */
interface AlertNotifier {
    val isEnabled: Boolean

    fun notify(key: String, title: String, detail: String)

    /**
     * 최근 발생한 장애 알림 이력(최신순, 최대 [limit]건) — 관리자 페이지 조회용.
     * 발송 스로틀·활성 여부와 독립하게 "장애 발생" 시점을 기록하는 구현만 실제 값을 돌려준다.
     * 기본은 빈 목록 — 이력을 보관하지 않는 목/단순 구현은 무영향(순수 추가).
     */
    fun findRecentAlerts(limit: Int): List<AlertRecord> = emptyList()

    /**
     * 관리자 "테스트 발송" — 발송 채널이 살아있는지 **동기**로 확인한다(스로틀·뮤트 무시).
     * [notify] 와 달리 결과(성공 여부)를 즉시 돌려줘야 해서 호출 스레드에서 발송하고, 2xx 면 true.
     * 발송 불가(비활성)·실패·예외는 모두 false — 절대 예외를 던지지 않는다. 기본은 false(목/단순 구현 무영향).
     */
    fun sendTest(): Boolean = false
}

/** 장애 알림 이력 1건 — 발생 시각(epoch ms)·스로틀 key·제목·상세. */
data class AlertRecord(
    val atEpochMs: Long,
    val key: String,
    val title: String,
    val detail: String,
)

/** alerts 조회 상한(관리자 API limit 절삭용). 버퍼 용량(200)보다 크게 둬도 실제 반환은 보관분까지다. */
const val ALERT_HISTORY_MAX_LIMIT = 500
