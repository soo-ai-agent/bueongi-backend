package io.soo.bueongi.api.v1

import io.soo.bueongi.domain.place.RemotePlaceProvider
import io.soo.bueongi.domain.share.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
