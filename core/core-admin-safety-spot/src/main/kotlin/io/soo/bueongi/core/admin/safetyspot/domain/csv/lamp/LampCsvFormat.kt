package io.soo.bueongi.core.admin.safetyspot.domain.csv.lamp

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvFormat
import io.soo.bueongi.core.enums.SafetySpotType

// 가로등·보안등 공용 — 두 종류가 같은 표준데이터 헤더(관리번호·설치위치)를 쓴다.
class LampCsvFormat(
    type: SafetySpotType,
) : SafetySpotCsvFormat(
        type = type,
        coordSystem = SafetySpotCoordSystem.WGS84,
        nameHeaders = listOf("보안등위치명", "관리번호"),
        addressHeaders = listOf("설치위치", "소재지지번주소"),
    )
