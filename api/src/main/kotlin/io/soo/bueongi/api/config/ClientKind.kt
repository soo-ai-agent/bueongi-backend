package io.soo.bueongi.api.config

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
    for ((marker, label) in USER_AGENT_MARKERS_IN_MATCH_ORDER) {
        if (agent.contains(marker, ignoreCase = true)) {
            return label
        }
    }
    return "other"
}

private val USER_AGENT_MARKERS_IN_MATCH_ORDER: List<Pair<String, String>> = listOf(
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
