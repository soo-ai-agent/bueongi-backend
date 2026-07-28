package io.soo.bueongi.api.v1

import io.soo.bueongi.api.v1.request.CreateShareRequest
import io.soo.bueongi.api.v1.request.EndShareRequest
import io.soo.bueongi.api.v1.request.SaveRouteRequest
import io.soo.bueongi.api.v1.request.UpdateLocationRequest
import io.soo.bueongi.api.v1.request.WatchRequest
import io.soo.bueongi.domain.share.CreateShareResponse
import io.soo.bueongi.domain.share.LocationView
import io.soo.bueongi.domain.share.RouteView
import io.soo.bueongi.domain.share.ShareService
import io.soo.bueongi.domain.share.UpdateLocationResponse
import io.soo.bueongi.domain.share.WatchResponse
import jakarta.validation.Valid
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 최소 위치 공유 서버(MVP 계약). 귀가 중인 사용자가 토큰을 발급받아 외부 메신저로 URL 을 공유하면,
 * 보호자는 토큰 URL 만으로(로그인 없이) 위치를 5초 폴링으로 본다. 보호자 회원가입/페어링/푸시는 범위 밖.
 *
 * 컨트롤러는 요청 검증·도메인 입력 변환(toReport/toStoredRoute)·보호자 HTML 렌더까지만 담당하고,
 * 생명주기 흐름은 [ShareService] 에 위임한다. 좌표 오류는 공통 RFC 7807 + `code`(400),
 * 위치 갱신 시 토큰 없음/만료는 404 SHARE_SESSION_NOT_FOUND, 보호자 조회는 항상 200(`expired` 플래그).
 *
 * 경로는 `/api` 프리픽스가 아닌 `/share` 하위 — 보호자가 브라우저로 직접 여는 공개 URL 이라 앱 전용 `/api` 와 분리한다.
 */
@RestController
@RequestMapping("/share")
class ShareController(
    private val service: ShareService,
) {
    /** 보호자 HTML(지도+5초 폴링 JS). 클래스패스에서 1회 로드해 캐시하고, 요청마다 토큰만 치환한다. */
    private val guardianHtmlTemplate: String by lazy {
        ClassPathResource("share/guardian.html").inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** POST /share/create → 토큰 발급. 본문은 선택(expires_in_hours: 1~3, eta_seconds: 선택 — 미도착 감시 기준). */
    @PostMapping("/create")
    fun create(@Valid @RequestBody(required = false) request: CreateShareRequest?): CreateShareResponse =
        service.create(request?.expiresInHours, request?.etaSeconds)

    /** POST /share/{token}/location → 위치 갱신(사용자 단말, 5초마다). 토큰 없음/만료 → 404. */
    @PostMapping("/{token}/location")
    fun updateLocation(
        @PathVariable token: String,
        @Valid @RequestBody request: UpdateLocationRequest,
    ): UpdateLocationResponse = service.updateLocation(token, request.ownerSecret, request.toReport())

    /** GET /share/{token}/location → 위치 조회(보호자 웹 5초 폴링). 항상 200, 상태는 `expired`/좌표 null 로 표현. */
    @GetMapping("/{token}/location")
    fun getLocation(@PathVariable token: String): LocationView = service.getLocation(token)

    /**
     * POST /share/{token}/route → 경로 컨텍스트 저장(앱이 길안내 시작 시 1회). 토큰 없음/만료·비밀 불일치 → 404.
     * 위치와 독립이라 기존 위치는 보존한다. 응답은 위치 갱신과 동일한 `{updated_at, expires_at}`.
     */
    @PostMapping("/{token}/route")
    fun saveRoute(
        @PathVariable token: String,
        @Valid @RequestBody request: SaveRouteRequest,
    ): UpdateLocationResponse = service.saveRoute(token, request.ownerSecret, request.toStoredRoute())

    /**
     * GET /share/{token}/route → 경로 컨텍스트 조회(보호자, 인증 없음·1회 fetch). 항상 200, 상태는 `expired`/`route` null 로 표현.
     * 위치 폴링(GET /location)과 달리 시청자로 집계하지 않는다.
     */
    @GetMapping("/{token}/route")
    fun getRoute(@PathVariable token: String): RouteView = service.getRoute(token)

    /**
     * POST /share/{token}/end → 공유 종료(사용자 도착/중단). 멱등이라 없는/이미 만료된 토큰도 200.
     * 종료 후 보호자의 `GET /{token}/location` 은 `expired:true` 가 되어 "공유 종료" 화면을 그린다.
     */
    @PostMapping("/{token}/end")
    fun end(
        @PathVariable token: String,
        @Valid @RequestBody request: EndShareRequest,
    ) = service.end(token, request.ownerSecret)

    /**
     * POST /share/{token}/watching → 보호자가 보고 있는지 + 현재 공유 위치 조회(사용자 본인 = 관리자 전용, owner_secret 필요).
     * 이 호출은 시청자로 집계되지 않으므로, 관리자(본인)의 상태 확인·미리보기는 '시청 중' 알림을 만들지 않는다.
     */
    @PostMapping("/{token}/watching")
    fun watching(
        @PathVariable token: String,
        @Valid @RequestBody request: WatchRequest,
    ): WatchResponse = service.getWatchingStatus(token, request.ownerSecret)

    /**
     * GET /share/{token} → 보호자 지도 페이지(HTML). 토큰 유효성과 무관하게 페이지를 주고,
     * 폴링 JS 가 `GET /share/{token}/location` 의 `expired` 로 만료/대기/라이브 상태를 그린다.
     * 토큰은 JS 리터럴로 안전 치환(아래 [escapeJs]) — base64url 문자만이라 실질 위험은 없으나 방어적으로 이스케이프.
     */
    @GetMapping("/{token}", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(@PathVariable token: String): String =
        guardianHtmlTemplate.replace(TOKEN_PLACEHOLDER, escapeJs(token))

    /** JS 문자열 리터럴 안전 치환용 최소 이스케이프(base64url 토큰엔 사실상 불필요하나 방어적). */
    private fun escapeJs(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace(">", "\\u003e")

    private companion object {
        const val TOKEN_PLACEHOLDER = "__SHARE_TOKEN__"
    }
}
