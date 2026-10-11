package io.soo.bueongi.core.admin.safetyspot.domain.csv.bell

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvContract
import io.soo.bueongi.core.enums.SafetySpotCoordSystem
import io.soo.bueongi.core.enums.SafetySpotType

// 비상벨 — EPSG:5174(옛 한국 측지계 평면좌표) X·Y 를 위경도로 바꿔 적재한다.
class BellCsvContract : SafetySpotCsvContract(
    type = SafetySpotType.BELL,
    coordSystem = SafetySpotCoordSystem.EPSG5174,
    nameHeaders = listOf("설치장소"),
    addressHeaders = listOf("소재지지번주소"),
)
