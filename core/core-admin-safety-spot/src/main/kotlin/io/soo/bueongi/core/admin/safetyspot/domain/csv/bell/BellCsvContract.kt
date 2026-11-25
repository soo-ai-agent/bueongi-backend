package io.soo.bueongi.core.admin.safetyspot.domain.csv.bell

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvContract
import io.soo.bueongi.core.enums.SafetySpotType

// 비상벨 — 공공데이터포털 원본은 WGS84위도·WGS84경도·설치위치를 쓴다. 옛 판의 X·Y(EPSG:5174)·설치장소도 받는다.
class BellCsvContract : SafetySpotCsvContract(
    type = SafetySpotType.BELL,
    coordSystem = SafetySpotCoordSystem.EPSG5174,
    latHeaders = listOf("WGS84위도", "위도"),
    lngHeaders = listOf("WGS84경도", "경도"),
    nameHeaders = listOf("설치위치", "설치장소"),
    addressHeaders = listOf("소재지지번주소"),
)
