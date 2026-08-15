package io.soo.bueongi.storage.db.core.facility.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacilityCollectionDto(
    val type: String? = null,
    val items: List<FacilityItemDto> = emptyList(),
)
