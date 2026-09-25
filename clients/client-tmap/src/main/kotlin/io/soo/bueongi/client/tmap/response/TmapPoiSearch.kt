package io.soo.bueongi.client.tmap.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.soo.bueongi.client.tmap.TmapPlace

// 0건 응답은 칸을 생략하고 오므로 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapPoiSearch(val searchPoiInfo: TmapPoiList? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapPoiList(val pois: TmapPois? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapPois(val poi: List<TmapPoi> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapPoi(
    val name: String? = null,
    val frontLat: Double? = null,
    val frontLon: Double? = null,
    val newAddressList: TmapNewAddressList? = null,
) {
    fun toTmapPlace(): TmapPlace? {
        val lat: Double = frontLat ?: return null
        val lng: Double = frontLon ?: return null
        val placeName: String = name?.trim().orEmpty()
        if (placeName.isEmpty()) {
            return null
        }
        val address: String = newAddressList?.newAddress?.firstOrNull()?.fullAddressRoad?.trim().orEmpty()
        return TmapPlace(name = placeName, address = address, lat = lat, lng = lng)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapNewAddressList(val newAddress: List<TmapNewAddress> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapNewAddress(val fullAddressRoad: String? = null)
