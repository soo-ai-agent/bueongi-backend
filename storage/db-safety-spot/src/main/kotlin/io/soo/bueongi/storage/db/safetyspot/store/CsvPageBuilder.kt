package io.soo.bueongi.storage.db.safetyspot.store

// CCTV 원천은 수십만 행이라 전부 메모리에 올리지 않고, 요청한 쪽의 행만 모은다.
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

    /**
     * 행 판정 하나를 센다. 이 행이 요청한 쪽에 들어가면 true — 그때만 칸을 나눠 [add] 한다.
     * 전체·적재·빠짐 수는 파일 전체 기준이고, 거르기(빠진 행만·검색어)는 쪽 배치에만 걸린다.
     */
    fun count(result: CsvRowResult, cells: List<String>): Boolean {
        totalRows++
        when (result) {
            is CsvRowResult.Loaded -> loadedRows++
            is CsvRowResult.Skipped -> skippedRows++
        }
        if (skippedOnly && result !is CsvRowResult.Skipped) {
            return false
        }
        // 결합 문자열은 검색할 때만 만든다 — CCTV 37만 행을 검색어 없이 넘길 때의 낭비가 크다.
        if (keyword.isNotBlank() && !cells.joinToString(" ").contains(keyword, ignoreCase = true)) {
            return false
        }
        val position: Int = matchedRows
        matchedRows++
        val firstOnPage: Int = page * size
        return position >= firstOnPage && position < firstOnPage + size
    }

    fun add(row: CsvRow) {
        rows.add(row)
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
}
