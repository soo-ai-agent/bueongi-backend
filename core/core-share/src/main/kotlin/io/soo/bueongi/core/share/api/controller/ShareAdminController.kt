package io.soo.bueongi.core.share.api.controller

import io.soo.bueongi.core.share.api.response.ShareSessionsResponse
import io.soo.bueongi.core.share.api.response.ShareStatusResponse
import io.soo.bueongi.core.share.domain.model.ActiveShareView
import io.soo.bueongi.core.share.domain.model.LocationView
import io.soo.bueongi.core.share.domain.route.model.RouteView
import io.soo.bueongi.core.share.domain.service.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class ShareAdminController(private val shareService: ShareService) {
    @GetMapping("/share-status")
    fun shareStatus(): ShareStatusResponse {
        val activeSessions: Int = shareService.countActiveSessions()
        return ShareStatusResponse(activeSessions = activeSessions)
    }

    @GetMapping("/share-sessions")
    fun shareSessions(): ShareSessionsResponse {
        val sessions: List<ActiveShareView> = shareService.listActiveSessions()
        return ShareSessionsResponse(sessions = sessions)
    }

    @GetMapping("/share-sessions/{token}/location")
    fun shareSessionLocation(
        @PathVariable token: String,
    ): LocationView {
        return shareService.getLocationForAdmin(token)
    }

    @GetMapping("/share-sessions/{token}/route")
    fun shareSessionRoute(
        @PathVariable token: String,
    ): RouteView {
        return shareService.getRoute(token)
    }
}
