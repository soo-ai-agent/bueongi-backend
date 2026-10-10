package io.soo.bueongi.client.tmap.place.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.soo.bueongi.client.place.Place

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapPoiSearch(
    val searchPoiInfo: TmapPoiList = TmapPoiList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapPoiList(
    val pois: TmapPois = TmapPois(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapPois(
    val poi: List<TmapPoi> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapPoi(
    val name: String = "",
    // 좌표는 명세상 선택(N)이고 대신 쓸 값이 없다 — 없으면 toPlace 가 그 POI 를 버린다.
    val frontLat: Double? = null,
    val frontLon: Double? = null,
    val newAddressList: TmapNewAddressList = TmapNewAddressList(),
) {
    fun toPlace(): Place? {
        val lat: Double = frontLat ?: return null
        val lng: Double = frontLon ?: return null
        val placeName: String = name.trim()
        if (placeName.isEmpty()) {
            return null
        }
        val address: String = newAddressList.newAddress.firstOrNull()?.fullAddressRoad.orEmpty().trim()
        return Place(name = placeName, address = address, lat = lat, lng = lng)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapNewAddressList(
    val newAddress: List<TmapNewAddress> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TmapNewAddress(
    val fullAddressRoad: String = "",
)
