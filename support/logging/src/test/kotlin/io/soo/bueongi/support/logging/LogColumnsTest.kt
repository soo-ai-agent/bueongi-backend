package io.soo.bueongi.support.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 세 줄이 같은 자리에 같은 뜻의 값을 두어야 눈이 세로로 훑는다.
 * 대상(경로) 칸의 폭이 안 맞으면 그 뒤 칸(소요시간·표식)이 줄마다 다른 열에 떨어진다.
 */
class LogColumnsTest {

    @Test
    fun `짧은 경로도 대상 칸 폭까지 채운다`() {
        val short: String = padColumn("/wp-login.php", TARGET_COLUMN_WIDTH)
        val long: String = padColumn("/api/admin/share-sessions/{token}/location", TARGET_COLUMN_WIDTH)

        assertThat(short).hasSize(TARGET_COLUMN_WIDTH)
        assertThat(long).hasSize(TARGET_COLUMN_WIDTH)
    }

    /** 실경로 중 가장 긴 것이 폭 안에 들어와야 한다 — 넘으면 그 줄만 뒤 칸이 밀린다. */
    @Test
    fun `가장 긴 실경로가 대상 칸에 들어간다`() {
        assertThat("/api/admin/share-sessions/{token}/location".length)
            .isLessThanOrEqualTo(TARGET_COLUMN_WIDTH)
    }

    @Test
    fun `폭을 넘는 값은 자르지 않고 그대로 둔다`() {
        val overflow: String = "/" + "a".repeat(TARGET_COLUMN_WIDTH + 10)

        assertThat(padColumn(overflow, TARGET_COLUMN_WIDTH)).isEqualTo(overflow)
    }

    /** 한글은 터미널에서 두 칸이라, 글자 수로 채우면 한글이 섞인 줄만 오른쪽으로 밀린다. */
    @Test
    fun `한글이 섞인 대상은 표시 폭으로 채운다`() {
        val korean: String = padColumn("외부 장소 검색", TARGET_COLUMN_WIDTH)

        // 한글 6자 = 12칸, 공백 2칸 → 표시 폭 14. 나머지를 채워 글자 수는 그만큼 적다.
        assertThat(korean.length).isLessThan(TARGET_COLUMN_WIDTH)
        assertThat(korean).endsWith(" ")
    }
}
