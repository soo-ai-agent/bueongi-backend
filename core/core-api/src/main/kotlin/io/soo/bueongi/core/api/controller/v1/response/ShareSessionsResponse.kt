package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.api.controller.v1.request.SlackConfigUpdateRequest
import io.soo.bueongi.core.domain.share.ActiveShareView
import io.soo.bueongi.core.domain.share.LocationView
import io.soo.bueongi.core.domain.share.ShareService
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_DEFAULT_LIMIT
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_MAX_LIMIT
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class ShareSessionsResponse(
    val sessions: List<ActiveShareView>,
)
