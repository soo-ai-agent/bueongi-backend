package io.soo.bueongi.share

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
 * 최소 위치 공유 서버(MVP 계약 4개 엔드포인트). 귀가 중인 사용자가 토큰을 발급받아 외부 메신저로 URL 을 공유하면,
 * 보호자는 토큰 URL 만으로(로그인 없이) 위치를 5초 폴링으로 본다. 보호자 회원가입/페어링/푸시는 범위 밖.
 *
 * 저장은 [LocationStore](Redis/인메모리 폴백)에 위임 — Redis 미주입 시 인메모리로 동작(MVP·CI 무중단).
 * 좌표 오류는 공통 RFC 7807 + `code`(400), 위치 갱신 시 토큰 없음/만료는 404 SHARE_SESSION_NOT_FOUND.
 * 보호자 조회(GET location)는 항상 200 으로 `expired` 플래그를 실어 보호자 웹이 만료 화면을 그릴 수 있게 한다.
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

    /** POST /share/create → 토큰 발급. 본문은 선택(expires_in_hours: 1~3, 미지정 시 기본). */
    @PostMapping("/create")
    fun create(@Valid @RequestBody(required = false) request: CreateShareRequest?): CreateShareResponse =
        service.create(request)

    /** POST /share/{token}/location → 위치 갱신(사용자 단말, 5초마다). 토큰 없음/만료 → 404. */
    @PostMapping("/{token}/location")
    fun updateLocation(
        @PathVariable token: String,
        @Valid @RequestBody request: UpdateLocationRequest,
    ): UpdateLocationResponse = service.updateLocation(token, request)

    /** GET /share/{token}/location → 위치 조회(보호자 웹 5초 폴링). 항상 200, 상태는 `expired`/좌표 null 로 표현. */
    @GetMapping("/{token}/location")
    fun getLocation(@PathVariable token: String): LocationView = service.getLocation(token)

    /**
     * POST /share/{token}/end → 공유 종료(사용자 도착/중단). 멱등이라 없는/이미 만료된 토큰도 200.
     * 종료 후 보호자의 `GET /{token}/location` 은 `expired:true` 가 되어 "공유 종료" 화면을 그린다.
     */
    @PostMapping("/{token}/end")
    fun end(
        @PathVariable token: String,
        @Valid @RequestBody request: EndShareRequest,
    ) = service.end(token, request)

    /**
     * POST /share/{token}/watching → 보호자가 보고 있는지 + 현재 공유 위치 조회(사용자 본인 = 관리자 전용, owner_secret 필요).
     * 이 호출은 시청자로 집계되지 않으므로, 관리자(본인)의 상태 확인·미리보기는 '시청 중' 알림을 만들지 않는다.
     * 위치를 함께 실어 관리자가 보호자에게 보이는 화면을 '따로' 미리볼 수 있다(읽기 토큰 GET /location 과 분리).
     */
    @PostMapping("/{token}/watching")
    fun watching(
        @PathVariable token: String,
        @Valid @RequestBody request: WatchRequest,
    ): WatchResponse = service.watchingStatus(token, request)

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
