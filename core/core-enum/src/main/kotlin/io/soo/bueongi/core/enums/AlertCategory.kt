package io.soo.bueongi.core.enums

// 운영 알림의 종류. 알림을 보내는 곳·Slack 뮤트 목록·관리자 알림 이력이 이 하나를 같이 쓴다.
enum class AlertCategory(
    val key: String,
    val label: String,
    val severity: AlertSeverity,
) {
    SERVER_ERROR("api-500", "서버 오류(미처리 500)", AlertSeverity.CRITICAL),

    PLACE_SEARCH_FAILED("place-search-failed", "장소 검색 실패", AlertSeverity.DEGRADED),

    ROUTE_SEARCH_FAILED("tmap-route-failed", "경로 검색 실패", AlertSeverity.DEGRADED),

    MISSED_ARRIVAL("missed-arrival", "미도착 의심", AlertSeverity.CRITICAL),

    SHARE_STORE_UNAVAILABLE("share-store-unavailable", "위치 공유 저장소 장애", AlertSeverity.CRITICAL),

    SAFETY_SPOT_FETCH_FAILED("safety-spot-fetch-failed", "시설 수집 실패", AlertSeverity.DEGRADED),

    SAFETY_SPOT_NO_COORDINATES("safety-spot-no-coordinates", "시설 좌표 없음", AlertSeverity.DEGRADED),
    ;

    // 같은 종류라도 대상마다 따로 스로틀하도록 key 뒤에 대상을 붙인다.
    // Slack 쪽(SlackAlertConfig.isMuted)은 ':' 앞부분을 종류로 읽어 뮤트를 판정한다.
    fun keyOf(target: String): String {
        return "$key$DELIMITER$target"
    }

    companion object {
        private const val DELIMITER: String = ":"

        fun ofAlertKey(alertKey: String): AlertCategory? {
            val categoryKey: String = alertKey.substringBefore(DELIMITER)
            for (category in entries) {
                if (category.key == categoryKey) {
                    return category
                }
            }
            return null
        }
    }
}
