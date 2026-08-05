package io.soo.bueongi.client.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Kakao Local 장소 1건. 도로명주소(road_address_name)를 우선하고 없으면 지번주소(address_name)로 폴백한다.
 * 좌표 x=경도(lng), y=위도(lat)는 문자열로 오므로 Double 로 파싱한다 — 필수 필드 누락/파싱 실패 건은 [toKakaoPlace] 에서 제외.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoPlaceDocument(
    @JsonProperty("place_name") val placeName: String? = null,
    @JsonProperty("road_address_name") val roadAddressName: String? = null,
    @JsonProperty("address_name") val addressName: String? = null,
    @JsonProperty("x") val lng: String? = null,
    @JsonProperty("y") val lat: String? = null,
) {
    /**
     * [KakaoPlace] 로 매핑. 이름/주소/좌표 중 하나라도 없거나 좌표 파싱 실패면 null(결과에서 제외).
     *
     * 좌표는 파싱 성공만으로 충분치 않다 — `"NaN".toDoubleOrNull()`=NaN, `"Infinity"`=∞, 범위를 벗어난 값도
     * 모두 파싱은 통과한다. 이런 비유효 좌표가 검색 결과로 새어 프론트→경로 요청 destination 으로
     * 흘러가면, haversine 이 NaN 거리를 내고 거리 가드(`<`/`>` 비교는 NaN 에 대해 항상 false)를 통과해
     * 무의미한 경로/시설을 만든다. 외부 데이터 경계에서 "파싱 실패 제외"와 같은 정책으로 비유효 좌표도 제외한다.
     */
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
