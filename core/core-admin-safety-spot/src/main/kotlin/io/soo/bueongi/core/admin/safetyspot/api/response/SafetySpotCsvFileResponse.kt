package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvFile
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvOrigin

data class SafetySpotCsvFileResponse(
    val origin: SafetySpotCsvOrigin,
    val type: SafetySpotType,
    val name: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    val readByServer: Boolean,
    val replacedByUpload: Boolean,
) {
    companion object {
        fun from(file: SafetySpotCsvFile): SafetySpotCsvFileResponse {
            return SafetySpotCsvFileResponse(
                origin = file.origin,
                type = file.type,
                name = file.name,
                sizeBytes = file.sizeBytes,
                modifiedAtMs = file.modifiedAtMs,
                readByServer = file.readByServer,
                replacedByUpload = file.replacedByUpload,
            )
        }

        fun from(files: List<SafetySpotCsvFile>): List<SafetySpotCsvFileResponse> {
            val responses: ArrayList<SafetySpotCsvFileResponse> = ArrayList(files.size)
            for (file in files) {
                responses.add(from(file))
            }
            return responses
        }
    }
}
