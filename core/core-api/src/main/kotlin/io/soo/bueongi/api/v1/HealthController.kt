package io.soo.bueongi.api.v1

import io.soo.bueongi.domain.place.RemotePlaceProvider
import io.soo.bueongi.domain.share.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 운영 점검(readiness) API. 서비스 가동 여부와 **활성 장소 검색 소스**(외부 실연동 vs 내장 시드)를
 * 시크릿 노출 없이 알려준다.
 *
 * 동기: consensus 의 미해결 리스크 "실 Kakao 키 운영검증(키 주입 시점)" — 운영에 키가 올바로 주입됐는지
 * 확인하려면 키 값을 보지 않고도 "실연동이 켜졌는가"를 알 수 있어야 한다. [placeSource] 는
 * [RemotePlaceProvider.isEnabled] 라는 **불리언에서 파생한 라벨**("live"/"seed")일 뿐, 키 문자열은 절대 응답에 싣지 않는다.
 * ops/오케스트레이터는 `placeSource="live"` 로 키 배선을, `"seed"` 로 폴백 동작을 검증한다.
 *
 * [HealthStatus.locationStore] 도 같은 패턴이다 — 위치 공유 세션 저장소가 Redis("redis")인지 인메모리 폴백("memory")인지를
 * [ShareService.storeBackendLabel] 이 중계하는 식별자 파생 라벨로만 노출하고(컨트롤러가 storage 계층을
 * 직접 만지지 않는다 — 레이어 규칙), Redis URL·자격증명은 절대 응답에 싣지 않는다.
 */
@RestController
@RequestMapping("/api/health")
class HealthController(
    private val remoteProvider: RemotePlaceProvider,
    private val shareService: ShareService,
) {

    /** GET /api/health → 가동 상태 + 활성 장소 소스 + 위치공유 저장소(시크릿 비노출). */
    @GetMapping
    fun health(): HealthStatus {
        val placeSource: String = if (remoteProvider.isEnabled) {
            PLACE_SOURCE_LIVE
        } else {
            PLACE_SOURCE_SEED
        }
        return HealthStatus(
            status = STATUS_UP,
            service = SERVICE_NAME,
            placeSource = placeSource,
            locationStore = shareService.storeBackendLabel(),
        )
    }

    private companion object {
        const val STATUS_UP: String = "UP"
        const val SERVICE_NAME: String = "bueongi-backend"
        const val PLACE_SOURCE_LIVE: String = "live"
        const val PLACE_SOURCE_SEED: String = "seed"
    }
}
