package io.soo.bueongi.client.tmap.response

// Tmap 은 문자열 값 안에 NUL(0x00)을 끼워 보내 Jackson 이 파싱을 거부한다 — 앞뒤가 같으면 하나만, 다르면 공백으로 잇는다.
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
            out.setLength(out.length - left.length)
        } else if (left.isNotEmpty() && right.isNotEmpty()) {
            out.append(' ')
        }
        start = at + 1
    }
}

private fun tailAfterQuote(out: StringBuilder): String {
    var i: Int = out.length
    while (i > 0 && out[i - 1] != '"') {
        i--
    }
    return out.substring(i)
}

private fun valueEnd(body: String, from: Int): Int {
    var i: Int = from
    while (i < body.length && body[i] != '"' && body[i] != NUL) {
        i++
    }
    return i
}

private const val NUL: Char = '\u0000'
