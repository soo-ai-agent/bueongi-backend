package io.soo.bueongi.core.api.config

object RequestPathNormalizer {

    fun normalize(path: String): String {
        val segments: List<String> = path.split("/")
        if (!hasShareToken(segments)) {
            return path
        }
        val templated: MutableList<String> = segments.toMutableList()
        templated[SHARE_TOKEN_INDEX] = TOKEN_PLACEHOLDER
        return templated.joinToString("/")
    }

    private fun hasShareToken(segments: List<String>): Boolean {
        val hasTokenSegment: Boolean = segments.size > SHARE_TOKEN_INDEX
        if (!hasTokenSegment) {
            return false
        }
        val underShare: Boolean = segments[SHARE_ROOT_INDEX] == SHARE_ROOT
        val tokenSegment: String = segments[SHARE_TOKEN_INDEX]
        val isRealToken: Boolean = tokenSegment.isNotBlank() && tokenSegment !in SHARE_LITERAL_ACTIONS
        return underShare && isRealToken
    }

    private const val SHARE_ROOT_INDEX: Int = 1
    private const val SHARE_TOKEN_INDEX: Int = 2
    private const val SHARE_ROOT: String = "share"
    private const val TOKEN_PLACEHOLDER: String = "{token}"

    private val SHARE_LITERAL_ACTIONS: Set<String> = setOf("create")
}
