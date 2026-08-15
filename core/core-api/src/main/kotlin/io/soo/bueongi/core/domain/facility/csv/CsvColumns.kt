package io.soo.bueongi.core.domain.facility.csv

import io.soo.bueongi.core.enums.FacilityCoordSystem

/**
 * CSV 헤더에서 찾아 둔 컬럼 위치. 행마다 헤더를 다시 뒤지지 않으려고 한 번만 계산한다.
 * 인덱스는 밖으로 내보내지 않는다 — 행에서 값을 꺼내는 것까지 이 클래스가 한다.
 */
class CsvColumns(
    val useWgs84: Boolean,
    val coordSystem: FacilityCoordSystem,
    private val latIndex: Int?,
    private val lngIndex: Int?,
    private val xIndex: Int?,
    private val yIndex: Int?,
    private val nameIndex: Int?,
    private val addressIndex: Int?,
    private val purposeIndex: Int?,
    private val cameraCountIndex: Int?,
    private val phoneIndex: Int?,
) {
    fun name(row: List<String>): String? = row.at(nameIndex)

    fun address(row: List<String>): String? = row.at(addressIndex)

    fun purpose(row: List<String>): String? = row.at(purposeIndex)

    fun phone(row: List<String>): String? = row.at(phoneIndex)

    /** 0 이하는 값이 없는 것으로 본다(원천이 빈칸 대신 0 을 넣는 경우가 있다). */
    fun cameraCount(row: List<String>): Int? = row.at(cameraCountIndex)?.toIntOrNull()?.takeIf { it > 0 }

    fun latitude(row: List<String>): Double? = row.at(latIndex)?.toDoubleOrNull()

    fun longitude(row: List<String>): Double? = row.at(lngIndex)?.toDoubleOrNull()

    fun projectedX(row: List<String>): Double? = row.at(xIndex)?.toDoubleOrNull()

    fun projectedY(row: List<String>): Double? = row.at(yIndex)?.toDoubleOrNull()

    /** 컬럼이 없거나(index null) 행이 짧거나 빈칸이면 없음으로 본다 — 원천마다 컬럼 구성이 다르다. */
    private fun List<String>.at(index: Int?): String? {
        if (index == null || index >= size) {
            return null
        }
        return this[index].trim().takeIf { it.isNotEmpty() }
    }
}
