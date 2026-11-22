package io.soo.bueongi.client.tmap.place.response

import com.fasterxml.jackson.databind.JsonNode
import io.soo.bueongi.client.place.Place

// 좌표·이름·주소는 명세상 선택(N)이라 빠질 수 있다. path 는 빠진 칸을 빈 노드로 돌려주고,
// asText("") 는 빈 노드와 JSON null 을 빈 문자열로 읽는다 — 인자 없는 asText 는 null 을 "null" 로 읽는다.
internal fun parseTmapPoiSearch(root: JsonNode): List<Place> {
    val poiList: JsonNode = root.path("searchPoiInfo").path("pois").path("poi")
    val places: ArrayList<Place> = ArrayList(poiList.size())
    for (poi in poiList) {
        val lat: Double = poi.path("frontLat").asText("").toDoubleOrNull() ?: continue
        val lng: Double = poi.path("frontLon").asText("").toDoubleOrNull() ?: continue
        val name: String = poi.path("name").asText("").trim()
        if (name.isEmpty()) {
            continue
        }
        val firstAddress: JsonNode = poi.path("newAddressList").path("newAddress").path(0)
        val address: String = firstAddress.path("fullAddressRoad").asText("").trim()
        val place = Place(name = name, address = address, lat = lat, lng = lng)
        places.add(place)
    }
    return places
}
