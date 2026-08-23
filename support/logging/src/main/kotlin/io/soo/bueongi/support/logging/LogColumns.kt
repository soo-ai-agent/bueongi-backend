package io.soo.bueongi.support.logging

/**
 * 수신 요청·외부 송신·예외 세 줄이 함께 쓰는 칸 규격.
 *
 * 세 줄이 같은 자리에 같은 뜻의 값을 두어야 눈이 세로로 훑는다 —
 * 방향 · 상대 · 상태 · 대상 · (소요시간) · 부가 순이고, 앞의 세 칸은 폭을 고정한다.
 * 부가 칸은 줄마다 달라 표식(🔗 ↩ 📄 ✖ ↳ 👤 📱)으로 구분한다.
 */
const val LOG_COLUMN_SEPARATOR: String = "  |  "

/** `[수신] 관리자` · `[예외] 미처리` 가 들어가는 폭. */
const val DIRECTION_COLUMN_WIDTH: Int = 13

/** IPv6 로 적힌 로컬 주소(`0:0:0:0:0:0:0:1`)와 가장 긴 원천 이름(`safetydata`)이 들어가는 폭. */
const val COUNTERPART_COLUMN_WIDTH: Int = 16

/** `200 DELETE` 가 들어가는 폭. */
const val STATUS_COLUMN_WIDTH: Int = 10

/** 소요시간은 자릿수를 맞춰 오른쪽으로 붙인다 — `3005ms` 까지 들어간다. */
const val DURATION_COLUMN_WIDTH: Int = 7

/** 응답 코드 자리에 그대로 들어가도록 세 글자로 적는다 — 연결 자체가 실패해 코드가 없다는 뜻. */
const val NO_RESPONSE_STATUS: String = "---"

const val INBOUND_DIRECTION: String = "[수신]"
const val OUTBOUND_DIRECTION: String = "[송신]"
const val EXCEPTION_DIRECTION: String = "[예외]"

/**
 * 터미널에서 한글은 두 칸을 차지한다 — 글자 수로 채우면 한글이 섞인 줄만 오른쪽으로 밀린다.
 */
fun padColumn(text: String, width: Int): String {
    val missing: Int = width - displayWidth(text)
    if (missing <= 0) {
        return text
    }
    return text + " ".repeat(missing)
}

// ponytail: 두 칸을 차지하는 글자는 한글만 센다 — 로그 칸에 넣는 낱말이 한글과 ASCII 뿐이다.
//           한자·가나를 칸에 넣게 되면 그 영역도 여기에 더한다.
private fun displayWidth(text: String): Int {
    var width = 0
    for (character in text) {
        if (isHangul(character)) {
            width += 2
            continue
        }
        width += 1
    }
    return width
}

private fun isHangul(character: Char): Boolean {
    val code: Int = character.code
    val isSyllable: Boolean = code in 0xAC00..0xD7A3
    val isJamo: Boolean = code in 0x1100..0x11FF || code in 0x3130..0x318F
    return isSyllable || isJamo
}
