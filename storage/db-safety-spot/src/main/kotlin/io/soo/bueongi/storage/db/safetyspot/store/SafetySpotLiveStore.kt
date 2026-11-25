package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import java.time.Instant

// 실행 중 안심시설 쓰기 창구. 출처 조각 하나를 통째로 바꾼다(지우고 넣기를 한 트랜잭션으로).
interface SafetySpotLiveStore {
    // 관리자 업로드. 그 종류의 업로드와 배포 CDN 을 대신하고, 업로드에 든 지역의 API 조각을 지운다.
    fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>)

    // 외부 API 로 받은 지역 하나. 0건이어도 기록해 재수집 주기 동안 다시 묻지 않게 한다.
    fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>)

    // 그 지역 조각을 마지막으로 바꾼 시각. 받은 적이 없으면 null.
    fun savedAtOf(type: SafetySpotType, regionCode: String): Instant?
}
