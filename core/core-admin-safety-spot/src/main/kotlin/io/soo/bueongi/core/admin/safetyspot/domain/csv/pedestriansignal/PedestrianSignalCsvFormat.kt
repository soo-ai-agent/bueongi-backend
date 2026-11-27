package io.soo.bueongi.core.admin.safetyspot.domain.csv.pedestriansignal

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvFormat
import io.soo.bueongi.core.enums.SafetySpotType

// 보행신호등 — WGS84.
class PedestrianSignalCsvFormat : SafetySpotCsvFormat(
    type = SafetySpotType.PEDESTRIAN_SIGNAL,
    coordSystem = SafetySpotCoordSystem.WGS84,
    nameHeaders = listOf("신호등 종류", "신호등종류"),
    addressHeaders = listOf("주소", "소재지지번주소"),
)
