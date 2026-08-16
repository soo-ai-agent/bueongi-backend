package io.soo.bueongi.client.tmap

/**
 * Tmap 응답 본문을 JSON 으로 읽을 수 있게 다듬는다.
 *
 * Tmap 은 장소 이름을 문자열 값 안에서 **NUL(0x00)로 이어 붙여** 보낸다 —
 * `"name": "충림빌딩<NUL>충림빌딩"` 처럼 같은 이름이 두 번 온다.
 * JSON 규격상 제어문자(0x20 미만)는 이스케이프돼야 해서 Jackson 이 파싱을 거부하고,
 * 그대로 두면 길안내 문구에 NUL 이 섞여 프론트까지 나간다.
 *
 * 규칙: NUL 앞뒤가 같은 말이면 하나만 남기고, 다르면 공백으로 잇는다.
 */
fun sanitizeTmapJson(body: String): String {
    if (!body.contains(NUL)) {
        return body
    }
    val out = StringBuilder(body.length)
    var start: Int = 0
    while (true) {
        val at: Int = body.indexOf(NUL, start)
        if (at < 0) {
            out.append(body, start, body.length)
            return out.toString()
        }
        out.append(body, start, at)
        val left: String = tailAfterQuote(out)
        val right: String = body.substring(at + 1, valueEnd(body, at + 1))
        if (left.isNotEmpty() && right.startsWith(left)) {
            // "충림빌딩<NUL>충림빌딩에서 …" → 앞쪽 중복을 지우고 뒤쪽만 남긴다.
            out.setLength(out.length - left.length)
        } else if (left.isNotEmpty() && right.isNotEmpty()) {
            out.append(' ')
        }
        start = at + 1
    }
}

/** 열린 따옴표 뒤로 지금까지 쌓인 값의 끝부분. 앞뒤 중복 판별에만 쓴다. */
private fun tailAfterQuote(out: StringBuilder): String {
    var i: Int = out.length
    while (i > 0 && out[i - 1] != '"') {
        i--
    }
    return out.substring(i)
}

/** 값이 끝나는 자리 — 닫는 따옴표 또는 다음 NUL. */
private fun valueEnd(body: String, from: Int): Int {
    var i: Int = from
    while (i < body.length && body[i] != '"' && body[i] != NUL) {
        i++
    }
    return i
}

private const val NUL: Char = '\u0000'
