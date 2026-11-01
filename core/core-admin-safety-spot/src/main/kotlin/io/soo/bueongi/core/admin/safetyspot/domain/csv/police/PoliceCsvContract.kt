package io.soo.bueongi.core.admin.safetyspot.domain.csv.police

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvContract
import io.soo.bueongi.core.enums.SafetySpotType

// 치안시설 — EPSG:5179(국토지리정보원 평면좌표) X·Y 를 위경도로 바꿔 적재한다.
class PoliceCsvContract : SafetySpotCsvContract(
    type = SafetySpotType.POLICE,
    coordSystem = SafetySpotCoordSystem.EPSG5179,
    nameHeaders = listOf("시설명", "관서명"),
    addressHeaders = listOf("주소", "소재지도로명주소"),
    phoneHeaders = listOf("전화번호"),
)
