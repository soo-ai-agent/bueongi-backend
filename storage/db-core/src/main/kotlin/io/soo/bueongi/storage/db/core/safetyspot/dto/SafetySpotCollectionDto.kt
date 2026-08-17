package io.soo.bueongi.storage.db.core.safetyspot.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SafetySpotCollectionDto(
    // 종류를 적지 않은 파일은 null 이 가능함
    val type: String? = null,
    val items: List<SafetySpotItemDto> = emptyList(),
    // 파이프라인이 만든 CDN 파일에는 이 필드가 없어 null 이 가능함(우리가 저장한 파일에만 있다)
    val savedAt: String? = null,
)
