package io.soo.bueongi.storage.db.safetyspot.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class StoredSafetySpotProperties(
    val purpose: String? = null,
    val cameraCount: Int? = null,
)
