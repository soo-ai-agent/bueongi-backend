package io.soo.bueongi.core.admin.safetyspot.domain.csv.crimepronearea

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvFormat
import io.soo.bueongi.core.enums.SafetySpotType

// 범죄주의구간 — WGS84.
class CrimeProneAreaCsvFormat : SafetySpotCsvFormat(
    type = SafetySpotType.CRIME_PRONE_AREA,
    coordSystem = SafetySpotCoordSystem.WGS84,
    nameHeaders = listOf("시설명", "지역명"),
    addressHeaders = listOf("주소", "소재지지번주소"),
)
