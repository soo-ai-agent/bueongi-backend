package io.soo.bueongi.core.admin.safetyspot.domain.csv.model

class CsvColumns(
    val useWgs84: Boolean,
    val coordSystem: SafetySpotCoordSystem,
    private val latIndex: Int?,
    private val lngIndex: Int?,
    private val xIndex: Int?,
    private val yIndex: Int?,
    private val nameIndex: Int?,
    private val addressIndex: Int?,
    private val purposeIndex: Int?,
    private val cameraCountIndex: Int?,
    private val phoneIndex: Int?,
    private val regionCodeIndex: Int?,
) {
    fun name(row: List<String>): String? = textAt(row, nameIndex)

    fun address(row: List<String>): String? = textAt(row, addressIndex)

    fun purpose(row: List<String>): String? = textAt(row, purposeIndex)

    fun phone(row: List<String>): String? = textAt(row, phoneIndex)

    fun regionCode(row: List<String>): String? = textAt(row, regionCodeIndex)

    fun latitude(row: List<String>): Double? = numberAt(row, latIndex)

    fun longitude(row: List<String>): Double? = numberAt(row, lngIndex)

    fun projectedX(row: List<String>): Double? = numberAt(row, xIndex)

    fun projectedY(row: List<String>): Double? = numberAt(row, yIndex)

    fun cameraCount(row: List<String>): Int? {
        val text: String = textAt(row, cameraCountIndex) ?: return null
        val count: Int = text.toIntOrNull() ?: return null
        if (count <= 0) {
            return null
        }
        return count
    }

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
