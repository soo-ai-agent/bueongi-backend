package io.soo.bueongi.api.ads

import io.soo.bueongi.api.ads.request.AdImpressionRequest
import io.soo.bueongi.api.ads.request.placementValue
import io.soo.bueongi.domain.admin.AdStatsService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 광고 노출(호출) 집계용 공개 API. `/api/admin` 밖이라 무인증(SecurityConfig permitAll)이다 —
 * 프론트가 광고를 띄울 때마다 호출한다. 컨트롤러는 변환만 한다: 검증된 요청을 도메인 입력으로 바꿔
 * [AdStatsService.record] 에 위임하고 204 를 돌려준다. 집계 조회는 관리자 전용([io.soo.bueongi.api.admin.AdminController]).
 */
@RestController
@RequestMapping("/api/ads")
class AdController(
    private val adStatsService: AdStatsService,
) {

    /**
     * POST /api/ads/impression → 광고 노출 1건 기록 후 204 No Content.
     * placement 허용값(interstitial|native) 검증은 [AdImpressionRequest] 가 한다(누락·오값 → 400). 여기선 변환만.
     * 기록은 순수 부수효과다 — 저장 실패가 응답을 깨지 않는다([AdStatsService.record] → 저장소가 예외를 삼킨다).
     */
    @PostMapping("/impression")
    fun impression(@Valid @RequestBody request: AdImpressionRequest): ResponseEntity<Void> {
        adStatsService.record(request.placementValue())
        return ResponseEntity.noContent().build()
    }
}
