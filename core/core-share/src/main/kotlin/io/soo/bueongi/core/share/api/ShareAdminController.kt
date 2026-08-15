package io.soo.bueongi.core.share.api

import io.soo.bueongi.core.share.api.response.ShareSessionsResponse
import io.soo.bueongi.core.share.api.response.ShareStatusResponse
import io.soo.bueongi.core.share.domain.model.LocationView
import io.soo.bueongi.core.share.domain.service.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 관리자용 공유 조회. 경로가 /api/admin 아래인 것은 프론트 계약이다. */
@RestController
@RequestMapping("/api/admin")
class ShareAdminController(
    private val shareService: ShareService,
) {

    @GetMapping("/share-status")
    fun shareStatus(): ShareStatusResponse = ShareStatusResponse(activeSessions = shareService.countActiveSessions())

    @GetMapping("/share-sessions")
    fun shareSessions(): ShareSessionsResponse = ShareSessionsResponse(sessions = shareService.listActiveSessions())

    @GetMapping("/share-sessions/{token}/location")
    fun shareSessionLocation(@PathVariable token: String): LocationView = shareService.getLocationForAdmin(token)
}
