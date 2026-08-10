package io.soo.bueongi.client.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** Kakao Local 좌표 규격: x=경도(lng), y=위도(lat), 문자열로 온다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoPlaceDocument(
    @JsonProperty("place_name") val placeName: String? = null,
    @JsonProperty("road_address_name") val roadAddressName: String? = null,
    @JsonProperty("address_name") val addressName: String? = null,
    @JsonProperty("x") val lng: String? = null,
    @JsonProperty("y") val lat: String? = null,
) {
    fun toKakaoPlace(): KakaoPlace? {
        val name: String = placeName?.takeIf { it.isNotBlank() } ?: return null
        val address: String = roadAddressName?.takeIf { it.isNotBlank() }
            ?: addressName?.takeIf { it.isNotBlank() }
            ?: return null
        val latValue: Double = lat?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val lngValue: Double = lng?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        return KakaoPlace(name = name, address = address, lat = latValue, lng = lngValue)
    }
}
