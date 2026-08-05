package io.soo.bueongi.domain.admin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshRecord
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import org.springframework.stereotype.Service

/**
 * 시설 라이브 갱신 결과 3상태 — facility-status 응답 전용 판별자. 프론트 FacilityRefreshStatus('replaced'|'kept'|'never')와 대응한다.
 *
 * [wire] 가 와이어 문자열이다([JsonValue]) — lastRefreshStatus 필드가 이 값이라 직렬화가 정확히
 * replaced|kept|never 를 내야 한다. 역직렬화는 [from] 으로 고정한다([JsonCreator]).
 *
 * - NEVER: 아직 갱신 시도 없음(키 없는 원천 포함).
 * - REPLACED: 마지막 갱신에서 실제로 교체 성공(교체 건수 있음).
 * - KEPT: 갱신을 시도했으나 수집 실패·0건이라 기존 적재분을 유지.
 */
enum class FacilityRefreshStatus(@get:JsonValue val wire: String) {
    NEVER("never"),
    REPLACED("replaced"),
    KEPT("kept"),
    ;

    companion object {
        /** wire 문자열 → 상태. 알 수 없는 값은 예외(닫힌 집합) — 역직렬화 경계용. */
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityRefreshStatus = entries.first { it.wire == value }
    }
}
