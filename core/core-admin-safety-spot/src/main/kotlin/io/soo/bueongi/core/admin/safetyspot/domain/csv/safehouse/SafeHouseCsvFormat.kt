package io.soo.bueongi.core.admin.safetyspot.domain.csv.safehouse

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvFormat
import io.soo.bueongi.core.enums.SafetySpotType

// 아동안전지킴이집 — WGS84.
class SafeHouseCsvFormat : SafetySpotCsvFormat(
    type = SafetySpotType.SAFEHOUSE,
    coordSystem = SafetySpotCoordSystem.WGS84,
    nameHeaders = listOf("업소명"),
    addressHeaders = listOf("소재지도로명주소"),
)
