package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import java.io.File

data class SafetySpotCsvFile(
    val origin: SafetySpotCsvOrigin,
    val type: SafetySpotType,
    val name: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    /** false 면 서버가 이 파일을 읽지 않는다 — 파이프라인이 CDN JSON 으로 바꿔 쓰는 원천이다. */
    val readByServer: Boolean,
    val replacedByUpload: Boolean,
    internal val file: File,
)
