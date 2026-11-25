package io.soo.bueongi.storage.db.safetyspot.model

// 한 종류의 시설 수를 출처별로 — 파일(CDN, 업로드)과 외부 API 지역 수집. 같은 좌표는 한 곳으로 센다.
data class SafetySpotOriginCount(
    val fileCount: Int,
    val apiCount: Int,
)
