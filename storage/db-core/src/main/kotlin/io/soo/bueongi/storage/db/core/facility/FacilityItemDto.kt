package io.soo.bueongi.storage.db.core.facility
import io.soo.bueongi.core.enums.FacilityType

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.charset.Charset

/**
 * 시설/경로 1건. 시설은 lat/lng 점이고, safepath 는 좌표열(coords)을 가질 수 있다 —
 * coords 가 없으면 단일 점(lat/lng)을 1점짜리 경로로 본다. 비유효 좌표(NaN/∞/범위 밖)는 제외한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FacilityItemDto(
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val sigunguCode: String? = null,
    val coords: List<CoordDto>? = null,
    /** 소재지 주소(파이프라인 산출물 표준 필드). 표시용. */
    val address: String? = null,
    /** 전화번호(지구대·파출소 등). 표시·연결용. */
    val phone: String? = null,
    /** 파이프라인이 채우는 표시 메타(CCTV 용도/카메라대수 등). CSV 경로와 동형으로 적재. */
    val properties: FacilityItemPropertiesDto? = null,
) {
    fun toFacilityPoint(type: FacilityType): FacilityPoint? {
        val point: GeoPoint = GeoPoint.orNull(lat, lng) ?: return null
        return FacilityPoint(
            type = type,
            lat = point.lat,
            lng = point.lng,
            name = name,
            sigungu = sigunguCode,
            purpose = properties?.purpose,
            cameraCount = properties?.cameraCount,
            address = address?.trim()?.takeIf { it.isNotEmpty() },
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun toSafePath(): SafePath? {
        val coordList: List<GeoPoint>? = coords?.mapNotNull { it.toGeoPoint() }
        if (!coordList.isNullOrEmpty()) {
            return SafePath(coords = coordList, name = name)
        }
        val point: GeoPoint = GeoPoint.orNull(lat, lng) ?: return null
        return SafePath(coords = listOf(point), name = name)
    }
}
