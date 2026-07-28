package io.soo.bueongi.api.config

/**
 * 수신 요청 경로를 **집계용 템플릿**으로 정규화하는 단일 출처. 동적 세그먼트(공유 토큰)를 `{token}` 으로 치환해
 * 엔드포인트 카디널리티가 폭발하지 않게 한다 — 토큰마다 별도 엔드포인트로 집계되면 "엔드포인트별 호출수"가 무의미해진다.
 *
 * 규칙: 경로를 `/` 로 쪼개 `share` 하위의 **토큰 자리**(두 번째 세그먼트)만 `{token}` 으로 바꾸고 나머지는 원경로 그대로 둔다.
 *  - `/share/{token}`, `/share/{token}/location|end|watching|route` → 토큰 세그먼트만 치환.
 *  - `/share/create`(토큰이 아닌 고정 액션)와 `/api` 하위 경로는 그대로.
 *  - 알 수 없는 경로도 그대로 기록한다(누락 금지 — share 토큰만 특별 취급).
 */
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

    /** `/share/<토큰>` 형태인지 — 두 번째 세그먼트가 존재하고, 고정 액션(create)이 아닌 실토큰이어야 참. */
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

    /** `path.split("/")` 는 선행 `/` 때문에 index 0 이 빈 문자열이라, 루트는 1·토큰은 2 자리다. */
    private const val SHARE_ROOT_INDEX: Int = 1
    private const val SHARE_TOKEN_INDEX: Int = 2
    private const val SHARE_ROOT: String = "share"
    private const val TOKEN_PLACEHOLDER: String = "{token}"

    /** `/share` 하위에서 토큰이 아닌 고정 액션 세그먼트(치환하지 않는다). */
    private val SHARE_LITERAL_ACTIONS: Set<String> = setOf("create")
}
