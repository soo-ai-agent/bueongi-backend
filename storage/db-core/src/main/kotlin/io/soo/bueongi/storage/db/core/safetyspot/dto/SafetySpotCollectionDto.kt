package io.soo.bueongi.storage.db.core.safetyspot.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SafetySpotCollectionDto(
    val type: String? = null,
    val items: List<SafetySpotItemDto> = emptyList(),
)
