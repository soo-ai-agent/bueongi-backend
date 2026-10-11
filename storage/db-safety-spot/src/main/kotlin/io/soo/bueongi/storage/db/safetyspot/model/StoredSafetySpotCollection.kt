package io.soo.bueongi.storage.db.safetyspot.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class StoredSafetySpotCollection(
    val items: List<StoredSafetySpotItem> = emptyList(),
    val savedAt: String? = null,
)
