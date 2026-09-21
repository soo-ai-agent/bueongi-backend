package io.soo.bueongi.support.logging

// 수신·송신·예외 세 줄이 같은 자리에 같은 뜻의 값을 둬야 세로로 읽힌다 — 폭을 바꾸면 정렬 테스트가 깨진다.
const val LOG_COLUMN_SEPARATOR: String = "  |  "

const val DIRECTION_COLUMN_WIDTH: Int = 13

const val COUNTERPART_COLUMN_WIDTH: Int = 16

const val STATUS_COLUMN_WIDTH: Int = 10

// 실경로 중 가장 긴 `/api/admin/share-sessions/{token}/location` 이 42자다.
const val TARGET_COLUMN_WIDTH: Int = 42

const val DURATION_COLUMN_WIDTH: Int = 7

// 연결 자체가 실패해 응답 코드가 없다는 뜻 — 코드 칸 폭에 맞춰 세 글자다.
const val NO_RESPONSE_STATUS: String = "---"

const val INBOUND_DIRECTION: String = "[수신]"
const val OUTBOUND_DIRECTION: String = "[송신]"
const val EXCEPTION_DIRECTION: String = "[예외]"

// 터미널에서 한글은 두 칸을 차지한다 — 글자 수로 채우면 한글이 섞인 줄만 밀린다.
fun padColumn(text: String, width: Int): String {
    val missing: Int = width - displayWidth(text)
    if (missing <= 0) {
        return text
    }
    return text + " ".repeat(missing)
}

// ponytail: 두 칸 글자는 한글만 센다 — 칸에 넣는 낱말이 한글과 ASCII 뿐이다.
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
