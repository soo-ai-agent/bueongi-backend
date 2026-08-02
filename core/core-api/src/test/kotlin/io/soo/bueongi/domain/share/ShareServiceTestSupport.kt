package io.soo.bueongi.domain.share

import io.soo.bueongi.storage.db.core.share.InMemoryLocationStore

/** 공유 URL base — shareUrl 조립 검증에 쓴다. */
internal const val BASE_URL: String = "http://localhost:8119"

/**
 * [ShareService] 단위 테스트의 공용 토대. 공유 생명주기 불변식(추측 불가 토큰·자동 만료·expired 플래그)을
 * 결정론적 Clock 으로 고정한다. 저장소는 실제 [InMemoryLocationStore]. 관심사별 하위 클래스가 이 토대를 상속한다:
 * 생성/TTL/도착([ShareServiceCreateTest]) · 위치([ShareServiceLocationTest]) ·
 * 관리자 위치 조회([ShareServiceAdminLocationTest]) · 종료([ShareServiceEndTest]) ·
 * 시청 상태([ShareServiceWatchingTest]) · 경로([ShareServiceRouteTest]) ·
 * 활성 세션 목록([ShareServiceActiveSessionsTest]).
 */
abstract class ShareServiceTestSupport {

    protected val clock: MutableTestClock = MutableTestClock()
    protected val store: InMemoryLocationStore = InMemoryLocationStore(clock)
    protected val service: ShareService = ShareService(store, clock, BASE_URL)

    /** 위치 보고(도메인 입력). 쓰기 비밀은 updateLocation 호출부에서 별도 인자로 넘긴다. */
    protected fun report(lat: Double = 37.5, lng: Double = 127.0, remainingM: Double? = null, remainingS: Double? = null) =
        LocationReport(lat = lat, lng = lng, remainingM = remainingM, remainingS = remainingS)
}
