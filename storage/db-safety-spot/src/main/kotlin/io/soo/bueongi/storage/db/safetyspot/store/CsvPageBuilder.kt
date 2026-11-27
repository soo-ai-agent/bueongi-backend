package io.soo.bueongi.storage.db.safetyspot.store

// 행을 하나씩 받아, 요청한 쪽에 드는 행만 담는다. CCTV 원본은 수십만 행이라 전부 담지 않는다.
class CsvPageBuilder(
    private val page: Int,
    private val size: Int,
    private val skippedOnly: Boolean,
    private val keyword: String,
) {
    private val rows: ArrayList<CsvRow> = ArrayList()
    private var totalRows: Int = 0
    private var loadedRows: Int = 0
    private var skippedRows: Int = 0
    private var matchedRows: Int = 0

    // 전체, 적재, 빠짐 수는 파일 전체로 센다. '빠진 행만'과 검색어는 쪽에 담을 행을 고를 때만 쓴다.
    fun add(rowNumber: Int, cells: List<String>, result: CsvRowResult) {
        totalRows++
        when (result) {
            is CsvRowLoaded -> loadedRows++
            is CsvRowSkipped -> skippedRows++
        }
        if (!isMatched(cells, result)) {
            return
        }
        val matchedIndex: Int = matchedRows
        matchedRows++

        val firstOnPage: Int = page * size
        val isOnPage: Boolean = matchedIndex >= firstOnPage && matchedIndex < firstOnPage + size
        if (isOnPage) {
            rows.add(CsvRow(rowNumber, cells, result))
        }
    }

    fun build(header: List<String>): CsvPage {
        return CsvPage(
            header = header,
            rows = rows.toList(),
            totalRows = totalRows,
            loadedRows = loadedRows,
            skippedRows = skippedRows,
            matchedRows = matchedRows,
            page = page,
            size = size,
        )
    }

    private fun isMatched(cells: List<String>, result: CsvRowResult): Boolean {
        if (skippedOnly && result !is CsvRowSkipped) {
            return false
        }
        if (keyword.isBlank()) {
            return true
        }
        // 칸을 이어 붙인 글은 검색어가 있을 때만 만든다 — CCTV 37만 행을 검색어 없이 넘길 때 낭비가 크다.
        val rowText: String = cells.joinToString(" ")
        return rowText.contains(keyword, ignoreCase = true)
    }
}
