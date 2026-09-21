package io.soo.bueongi.support.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogColumnsTest {

    @Test
    fun `짧은 경로도 대상 칸 폭까지 채운다`() {
        val short: String = padColumn("/wp-login.php", TARGET_COLUMN_WIDTH)
        val long: String = padColumn("/api/admin/share-sessions/{token}/location", TARGET_COLUMN_WIDTH)

        assertThat(short).hasSize(TARGET_COLUMN_WIDTH)
        assertThat(long).hasSize(TARGET_COLUMN_WIDTH)
    }

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

    @Test
    fun `한글이 섞인 대상은 표시 폭으로 채운다`() {
        val korean: String = padColumn("외부 장소 검색", TARGET_COLUMN_WIDTH)

        // 한글 6자 = 12칸, 공백 2칸 → 표시 폭 14. 나머지를 채워 글자 수는 그만큼 적다.
        assertThat(korean.length).isLessThan(TARGET_COLUMN_WIDTH)
        assertThat(korean).endsWith(" ")
    }
}
