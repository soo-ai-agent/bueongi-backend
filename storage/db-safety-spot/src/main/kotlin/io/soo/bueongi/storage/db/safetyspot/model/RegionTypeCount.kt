package io.soo.bueongi.storage.db.safetyspot.model

import io.soo.bueongi.core.enums.SafetySpotType

// 자치단체 하나, 시설 종류 하나의 시설 수. regionCode 는 원천이 지역 코드를 주지 않은 시설이면 빈 문자열이다.
// 좌표 합계(latSum, lngSum)는 한국 범위 안의 시설만 더한 값이라 중심점을 구할 때 locatedCount 로 나눈다.
data class RegionTypeCount(
    val regionCode: String,
    val type: SafetySpotType,
    val total: Int,
    val apiCount: Int,
    val latSum: Double,
    val lngSum: Double,
    val locatedCount: Int,
)
