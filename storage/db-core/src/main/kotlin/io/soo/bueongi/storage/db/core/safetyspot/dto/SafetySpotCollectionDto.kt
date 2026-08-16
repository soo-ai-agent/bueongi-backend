package io.soo.bueongi.storage.db.core.safetyspot.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SafetySpotCollectionDto(
    // 종류를 적지 않은 파일은 null 이 가능함
    val type: String? = null,
    val items: List<SafetySpotItemDto> = emptyList(),
)
