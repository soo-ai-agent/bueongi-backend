package io.soo.bueongi.storage.db.safetyspot.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// CCTV 원천만 채우는 칸이고 그마저 비운 행이 있다.
@JsonIgnoreProperties(ignoreUnknown = true)
data class StoredSafetySpotProperties(
    val purpose: String? = null,
    val cameraCount: Int? = null,
)
