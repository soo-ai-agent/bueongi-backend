package io.soo.bueongi.core.admin.safetyspot.domain.csv.model

import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.store.RegionLocator

// 좌표로 지역 코드를 찾는다. 찾은 값을 기억하므로 업로드 한 번에 하나씩 새로 만들어 쓴다.
// ponytail: 약 1km 칸(위경도 소수 2자리)마다 한 번만 찾는다 — 지역 경계 1km 안의 행은 이웃 지역으로 붙을 수 있다.
// 정확도가 문제되면 행정경계 폴리곤으로 바꾼다.
class RegionCodeFinder(
    private val regionLocator: RegionLocator,
) {
    private val regionCodeByCell: HashMap<String, String?> = HashMap()

    fun find(location: GeoPoint): String? {
        val cell: String = "%.2f,%.2f".format(location.lat, location.lng)
        if (!regionCodeByCell.containsKey(cell)) {
            regionCodeByCell[cell] = regionLocator.locate(location)
        }
        return regionCodeByCell[cell]
    }
}
