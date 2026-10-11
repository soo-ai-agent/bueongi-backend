package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

// 두 교체 모두 원자적이다 — 조회는 교체 전/후 한쪽의 일관된 색인만 본다.
interface SafetySpotLiveStore {
    // 파일 출처(CDN·CSV·업로드)만 바꾸고, API 로 받은 지역 데이터는 남긴다.
    fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>)

    // 그 지역의 API 데이터만 바꾼다 — 다른 지역과 파일 출처는 그대로다.
    fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>)
}
