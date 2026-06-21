package io.soo.bueongi.health

import io.soo.bueongi.places.RemotePlaceProvider
import io.soo.bueongi.share.LocationStore
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
 * [LocationStore.backend] 라는 불리언/식별자 파생 라벨로만 노출하고, Redis URL·자격증명은 절대 응답에 싣지 않는다.
 */
@RestController
@RequestMapping("/api/health")
class HealthController(
    private val remoteProvider: RemotePlaceProvider,
    private val locationStore: LocationStore,
) {

    /** GET /api/health → 가동 상태 + 활성 장소 소스 + 위치공유 저장소(시크릿 비노출). */
    @GetMapping
    fun health(): HealthStatus = HealthStatus(
        status = STATUS_UP,
        service = SERVICE_NAME,
        placeSource = if (remoteProvider.isEnabled) PLACE_SOURCE_LIVE else PLACE_SOURCE_SEED,
        locationStore = locationStore.backend,
    )

    private companion object {
        const val STATUS_UP = "UP"
        const val SERVICE_NAME = "bueongi-backend"
        const val PLACE_SOURCE_LIVE = "live"
        const val PLACE_SOURCE_SEED = "seed"
    }
}

/**
 * 헬스/준비 상태 응답.
 * - [status]: 항상 "UP"(요청을 처리할 수 있으면 도달하므로). 향후 외부 의존 헬스체크 추가 시 확장 슬롯.
 * - [service]: 서비스 식별자.
 * - [placeSource]: "live"=외부 실연동(Kakao 키 주입됨) / "seed"=내장 시드 폴백. 키 설정 여부에서 파생한
 *   라벨일 뿐, 키 값 자체는 절대 노출하지 않는다.
 * - [locationStore]: "redis"=Redis 위치공유 저장소 / "memory"=인메모리 폴백. 저장소 종류 라벨일 뿐,
 *   Redis URL·자격증명은 절대 노출하지 않는다.
 */
data class HealthStatus(
    val status: String,
    val service: String,
    val placeSource: String,
    val locationStore: String,
)
