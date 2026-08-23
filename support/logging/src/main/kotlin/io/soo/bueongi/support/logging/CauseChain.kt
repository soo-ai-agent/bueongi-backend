package io.soo.bueongi.support.logging

/**
 * 겉 예외만 남기면 진짜 원인이 사라진다 — 실제로 파싱 오류가 겉의
 * "Error while extracting response" 뒤에 가려 오래 안 보였다(Tmap NUL 사건, 2026-08-15).
 * 스택 전체 대신 사슬만 한 줄로 적는다.
 *
 * 인증키는 예외 메시지에 URL·본문째로 실려 온다 — 마스킹은 여기서 한 번만 한다.
 */
fun causeChainOf(error: Throwable, vararg secrets: String): String {
    val chain = StringBuilder()
    var current: Throwable? = error
    var depth: Int = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (depth > 0) {
            chain.append(CAUSE_ARROW)
        }
        chain.append(current.javaClass.simpleName)
        val message: String = maskSecrets(current.message.orEmpty(), *secrets)
        if (message.isNotEmpty()) {
            chain.append(": ").append(message)
        }
        if (current.cause === current) {
            return chain.toString()
        }
        current = current.cause
        depth++
    }
    if (current != null) {
        chain.append(CAUSE_ARROW).append("…")
    }
    return chain.toString()
}

/**
 * 로그로 나가는 모든 남의 문장(예외 메시지·원천 응답문)이 지나는 문. 세 겹으로 막는다 —
 * 우리가 아는 키 값, URL 뒤 질의 문자열 전체, 그러고도 남은 `key=값` 꼴.
 * 너무 길면 로그를 덮으므로 잘라 낸다.
 */
fun maskSecrets(text: String, vararg secrets: String): String {
    var masked: String = text
    for (secret in secrets) {
        if (secret.isNotBlank()) {
            masked = masked.replace(secret, MASK)
        }
    }
    masked = URL_QUERY.replace(masked, "$1?$MASK")
    masked = SECRET_PARAM.replace(masked, "$1$MASK")
    if (masked.length > MAX_MESSAGE_LENGTH) {
        return masked.take(MAX_MESSAGE_LENGTH) + "…"
    }
    return masked
}

private const val MASK: String = "***"

private const val CAUSE_ARROW: String = " ← "

/** 순환·과다 로그 방지. */
private const val MAX_CAUSE_DEPTH: Int = 5

private const val MAX_MESSAGE_LENGTH: Int = 200

/** 질의 문자열에는 serviceKey·appKey 가 실린다 — 경로까지만 남기고 통째로 지운다. */
private val URL_QUERY: Regex = Regex("""(https?://[^\s"'<>]*?)\?[^\s"'<>]*""", RegexOption.IGNORE_CASE)

/**
 * URL 로 안 보이는 조각(본문·폼·오류 메시지)에 남은 인증 값.
 *
 * 이름 뒤에 무엇이 오든 지운다 — Kakao 는 401 본문에 `appKey(값) does not exist` 로
 * 괄호에 담아 돌려준다(실측). 앞의 lookbehind 는 `monkey=` 같은 낱말이 걸리는 것을 막는다.
 */
private val SECRET_PARAM: Regex = Regex(
    """(?<![A-Za-z0-9_])((?:service_?key|app_?key|auth_?key|esntl_?id|api_?key|token|secret|password|key)""" +
        """\s*[=:(\[]\s*"?)[^&\s"'<>,;)\]}]*""",
    RegexOption.IGNORE_CASE,
)
