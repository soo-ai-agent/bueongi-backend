package io.soo.bueongi.storage.db.core.safetyspot.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SafetySpotItemPropertiesDto(
    // CCTV 가 아닌 시설은 null 이 가능함
    val purpose: String? = null,
    // CCTV 가 아닌 시설은 null 이 가능함
    val cameraCount: Int? = null,
)
