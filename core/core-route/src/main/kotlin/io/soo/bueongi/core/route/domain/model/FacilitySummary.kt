package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.enums.FacilityPoiType
import com.fasterxml.jackson.annotation.JsonProperty

/** 경로변 시설 개수 요약. 각 값은 [FacilityPoiType] 별 마커 수다. */
data class FacilitySummary(
    val cctv: Int,
    val bell: Int,
    /**
     * 편의점 수. 이 레포의 다른 `…Store` 는 전부 저장소라 헷갈리기 쉬워 이름을 풀어 썼다.
     * 와이어 필드명은 프론트 계약이라 `store` 로 고정한다.
     */
    @JsonProperty("store")
    val convenienceStore: Int,
    val police: Int,
    /** 여성안심지킴이집 수 — [convenienceStore] 와 다른 시설이다. */
    val safehouse: Int,
    val lamp: Int,
    val pedestrianSignal: Int,
    val total: Int,
)
