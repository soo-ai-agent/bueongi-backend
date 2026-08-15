package io.soo.bueongi.storage.db.core.facility

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacilityItemPropertiesDto(
    val purpose: String? = null,
    val cameraCount: Int? = null,
)
