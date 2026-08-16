package io.soo.bueongi.core.safetyspot.domain.csv.model

import io.soo.bueongi.core.enums.SafetySpotCoordSystem

/**
 * CSV 헤더에서 찾아 둔 컬럼 위치. 행마다 헤더를 다시 뒤지지 않으려고 한 번만 계산한다.
 * 인덱스는 밖으로 내보내지 않는다 — 행에서 값을 꺼내는 것까지 이 클래스가 한다.
 */
class CsvColumns(
    val useWgs84: Boolean,
    val coordSystem: SafetySpotCoordSystem,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val latIndex: Int?,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val lngIndex: Int?,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val xIndex: Int?,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val yIndex: Int?,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val nameIndex: Int?,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val addressIndex: Int?,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val purposeIndex: Int?,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val cameraCountIndex: Int?,
    // 이 컬럼이 없는 파일은 null 이 가능함
    private val phoneIndex: Int?,
) {
    fun name(row: List<String>): String? =
        textAt(row, nameIndex)

    fun address(row: List<String>): String? =
        textAt(row, addressIndex)

    fun purpose(row: List<String>): String? =
        textAt(row, purposeIndex)

    fun phone(row: List<String>): String? =
        textAt(row, phoneIndex)

    fun latitude(row: List<String>): Double? =
        numberAt(row, latIndex)

    fun longitude(row: List<String>): Double? =
        numberAt(row, lngIndex)

    fun projectedX(row: List<String>): Double? =
        numberAt(row, xIndex)

    fun projectedY(row: List<String>): Double? =
        numberAt(row, yIndex)

    /** 0 이하는 값이 없는 것으로 본다 — 원천이 빈칸 대신 0 을 넣는 경우가 있다. */
    fun cameraCount(row: List<String>): Int? {
        val text: String = textAt(row, cameraCountIndex) ?: return null
        val count: Int = text.toIntOrNull() ?: return null
        if (count <= 0) {
            return null
        }
        return count
    }

    /** 컬럼이 없거나(index null) 행이 짧거나 빈칸이면 없음으로 본다 — 원천마다 컬럼 구성이 다르다. */
    private fun textAt(row: List<String>, index: Int?): String? {
        if (index == null || index >= row.size) {
            return null
        }
        val trimmed: String = row[index].trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return trimmed
    }

    private fun numberAt(row: List<String>, index: Int?): Double? {
        val text: String = textAt(row, index) ?: return null
        return text.toDoubleOrNull()
    }
}
