package io.soo.bueongi.support.logging

// 겉 예외만 남기면 진짜 원인이 가려진다 — Tmap NUL 파싱 오류가 "Error while extracting response" 뒤에 숨었다.
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

// 로그로 나가는 남의 문장(예외 메시지·원천 응답문)은 모두 이 문을 지난다.
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

private const val MAX_CAUSE_DEPTH: Int = 5

private const val MAX_MESSAGE_LENGTH: Int = 200

// 질의 문자열에 serviceKey·appKey 가 실린다 — 경로까지만 남기고 통째로 지운다.
private val URL_QUERY: Regex = Regex("""(https?://[^\s"'<>]*?)\?[^\s"'<>]*""", RegexOption.IGNORE_CASE)

// Kakao 는 401 본문에 `appKey(값) does not exist` 로 괄호에 담아 돌려준다(실측) — 이름 뒤 구분자를 넓게 잡는다.
private val SECRET_PARAM: Regex = Regex(
    """(?<![A-Za-z0-9_])((?:service_?key|app_?key|auth_?key|esntl_?id|api_?key|token|secret|password|key)""" +
        """\s*[=:(\[]\s*"?)[^&\s"'<>,;)\]}]*""",
    RegexOption.IGNORE_CASE,
)
