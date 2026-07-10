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
}
