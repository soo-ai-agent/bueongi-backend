package io.soo.bueongi.core.domain.facility

import io.soo.bueongi.core.enums.FacilityCoordSystem

/**
 * CSV 헤더에서 찾아 둔 컬럼 위치. 행마다 헤더를 다시 뒤지지 않으려고 한 번만 계산한다.
 * 해당 컬럼이 없으면 null — 원천마다 있는 컬럼이 달라 없음이 정상이다.
 */
data class CsvColumns(
    val useWgs84: Boolean,
    val coordSystem: FacilityCoordSystem,
    val lat: Int?,
    val lng: Int?,
    val x: Int?,
    val y: Int?,
    val name: Int?,
    val address: Int?,
    val purpose: Int?,
    val cameraCount: Int?,
    val phone: Int?,
)
