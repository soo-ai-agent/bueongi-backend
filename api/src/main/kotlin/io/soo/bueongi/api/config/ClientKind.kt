package io.soo.bueongi.api.config

/**
 * 어떤 종류의 클라이언트가 불렀는지.
 *
 * 앱이 스스로 밝힌 값(`X-Client-Platform`)을 먼저 믿고, 없으면 User-Agent 로 짐작한다.
 * UA 는 누구나 위조할 수 있으니 **보안 판단의 근거로 쓰지 않는다** — 무엇이 접속하는지
 * 사람이 읽으려는 표시다.
 */
fun clientKindOf(declaredPlatform: String?, userAgent: String?): String {
    val declared: String? = declaredPlatform?.trim()?.takeIf { it.isNotEmpty() }
    if (declared != null) {
        return declared.take(MAX_LABEL_LENGTH)
    }
    val agent: String = userAgent?.trim().orEmpty()
    if (agent.isEmpty()) {
        return "unknown"
    }
    return guessFromUserAgent(agent)
}

private fun guessFromUserAgent(agent: String): String {
    for ((marker, label) in USER_AGENT_MARKERS) {
        if (agent.contains(marker, ignoreCase = true)) {
            return label
        }
    }
    return "other"
}

/**
 * 위에서부터 먼저 걸리는 것을 쓴다 — 순서가 규칙이다.
 * 모바일 브라우저 UA 는 Chrome·Safari 를 함께 담으므로 Android·iPhone 을 먼저 본다.
 */
private val USER_AGENT_MARKERS: List<Pair<String, String>> = listOf(
    "okhttp" to "android-app",
    "CFNetwork" to "ios-app",
    "Expo" to "expo-go",
    "Android" to "android-web",
    "iPhone" to "ios-web",
    "iPad" to "ios-web",
    "Edg/" to "pc-edge",
    "Chrome" to "pc-chrome",
    "Firefox" to "pc-firefox",
    "Safari" to "pc-safari",
    "curl" to "curl",
    "python" to "script",
    "bot" to "bot",
)

private const val MAX_LABEL_LENGTH: Int = 40
