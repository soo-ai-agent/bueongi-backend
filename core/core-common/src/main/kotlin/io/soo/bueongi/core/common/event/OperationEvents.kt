package io.soo.bueongi.core.common.event

/**
 * 도메인이 "운영자가 알아야 할 일이 생겼다"를 알릴 때 발행하는 사건들.
 *
 * 도메인은 **무슨 일이 있었는지만** 말하고, 그걸 Slack 으로 보낼지 이메일로 보낼지는 모른다.
 * 받는 쪽은 core-ops 다 — 도메인끼리 서로를 참조하지 않도록 사건 타입만 여기 공용 모듈에 둔다.
 */
sealed interface OperationEvent {
    /** 같은 사건을 반복 발송하지 않기 위한 묶음 키. */
    val key: String
    val title: String
    val detail: String
}

/** Tmap 경로 후보를 하나도 못 받아 직선거리 추정으로 응답했다. 사용자는 실제 길이 아닌 것을 모른다. */
data class SafeRouteFellBack(
    override val detail: String,
) : OperationEvent {
    override val key: String = "tmap-route-fallback"
    override val title: String = "Tmap 경로 API 실패"
}

/** 외부 장소 검색이 실패해 시드 카탈로그로 답했다. */
data class PlaceSearchFellBack(
    override val detail: String,
) : OperationEvent {
    override val key: String = "kakao-place-fallback"
    override val title: String = "Kakao 장소검색 실패"
}

/** 예정 도착 시간을 넘겼는데 위치 보고도 끊겼다. [maskedToken] 은 앞 6자만 남긴 값이다(PII 금지). */
data class MissedArrivalSuspected(
    val maskedToken: String,
    override val detail: String,
) : OperationEvent {
    override val key: String = "missed-arrival:$maskedToken"
    override val title: String = "미도착 의심 — 위치 공유"
}

/** 안심시설 색인이 통째로 비어 경로변 마커를 만들 수 없다. */
data class FacilityIndexEmpty(
    override val detail: String,
) : OperationEvent {
    override val key: String = "facility-index-empty"
    override val title: String = "안심시설 데이터 제공 실패"
}

/** 위치 공유 저장소(Redis)에 닿지 못했다. */
data class ShareStoreUnavailable(
    override val detail: String,
) : OperationEvent {
    override val key: String = "share-store-unavailable"
    override val title: String = "위치 공유 저장소(Redis) 접근 실패"
}

/** 아무도 처리하지 않은 예외가 500 으로 나갔다. */
data class UnhandledServerError(
    val exceptionName: String,
    override val detail: String,
) : OperationEvent {
    override val key: String = "api-500:$exceptionName"
    override val title: String = "처리되지 않은 서버 오류(500)"
}
