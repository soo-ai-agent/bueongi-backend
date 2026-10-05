package io.soo.bueongi.storage.db.core.safetyspot

import io.soo.bueongi.storage.db.core.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvPageBuilder
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvRow
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvRowResult
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvSkipReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CsvPageBuilderTest {
    private val skipped: CsvRowResult = CsvRowResult.Skipped(CsvSkipReason.NO_COORDINATE)

    @Test
    fun `요청한 쪽의 행만 담고 집계는 파일 전체로 센다`() {
        val page: CsvPage = fill(CsvPageBuilder(page = 1, size = 2, skippedOnly = false, keyword = ""), 5)

        assertThat(page.rows.map { it.rowNumber }).containsExactly(3, 4)
        assertThat(page.totalRows).isEqualTo(5)
        assertThat(page.matchedRows).isEqualTo(5)
    }

    @Test
    fun `빠진 행만 보기는 빠진 행으로 쪽을 나눈다`() {
        val page: CsvPage = fill(CsvPageBuilder(page = 0, size = 10, skippedOnly = true, keyword = ""), 5)

        assertThat(page.rows.map { it.rowNumber }).containsExactly(2, 4)
        assertThat(page.matchedRows).isEqualTo(2)
        assertThat(page.loadedRows).isEqualTo(0)
        assertThat(page.skippedRows).isEqualTo(2)
    }

    @Test
    fun `검색어는 행 내용에 담긴 행으로만 쪽을 나누고, 집계는 그대로 파일 전체다`() {
        val builder = CsvPageBuilder(page = 0, size = 10, skippedOnly = false, keyword = "종로")
        builder.countAndAdd(1, "서울 종로구 세종대로")
        builder.countAndAdd(2, "부산 해운대구")
        builder.countAndAdd(3, "서울 종로구 평창동")
        val page: CsvPage = builder.build(listOf("머리글"))

        assertThat(page.rows.map { it.rowNumber }).containsExactly(1, 3)
        assertThat(page.matchedRows).isEqualTo(2)
        assertThat(page.totalRows).isEqualTo(3)
    }

    @Test
    fun `검색어는 대소문자를 가리지 않는다`() {
        val builder = CsvPageBuilder(page = 0, size = 10, skippedOnly = false, keyword = "cctv")
        builder.countAndAdd(1, "서울 CCTV 교차로")
        val page: CsvPage = builder.build(listOf("머리글"))

        assertThat(page.rows.map { it.rowNumber }).containsExactly(1)
    }

    private fun CsvPageBuilder.countAndAdd(rowNumber: Int, text: String) {
        if (count(skipped, text)) {
            add(CsvRow(rowNumber, listOf(text), skipped))
        }
    }

    // 짝수 번째 행만 빠지고, 나머지는 서버가 읽지 않는 행이다.
    private fun fill(builder: CsvPageBuilder, rowCount: Int): CsvPage {
        for (rowNumber in 1..rowCount) {
            val result: CsvRowResult = if (rowNumber % 2 == 0) skipped else CsvRowResult.NotRead
            if (builder.count(result, "칸")) {
                builder.add(CsvRow(rowNumber, listOf("칸"), result))
            }
        }
        return builder.build(listOf("머리글"))
    }
}
