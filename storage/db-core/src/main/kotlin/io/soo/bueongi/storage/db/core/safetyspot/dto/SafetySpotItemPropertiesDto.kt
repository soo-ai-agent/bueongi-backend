package io.soo.bueongi.storage.db.core.safetyspot.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SafetySpotItemPropertiesDto(val purpose: String? = null, val cameraCount: Int? = null)
