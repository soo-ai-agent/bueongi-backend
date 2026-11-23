package io.soo.bueongi.storage.db.safetyspot.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class StoredSafetySpotCollection(
    val items: List<StoredSafetySpotItem> = emptyList(),
    // CDN 컬렉션에는 없고 지역 캐시·업로드 파일만 쓴다 — 없으면 지역 캐시를 다시 받는다.
    val savedAt: String? = null,
)
