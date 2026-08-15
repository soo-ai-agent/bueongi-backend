package io.soo.bueongi.admin

import io.soo.bueongi.admin.request.SlackConfigUpdateRequest
import io.soo.bueongi.share.ActiveShareView
import io.soo.bueongi.share.LocationView
import io.soo.bueongi.share.ShareService
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_DEFAULT_LIMIT
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_MAX_LIMIT
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.support.monitoring.ALERT_HISTORY_MAX_LIMIT
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord
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

data class ShareStatusResponse(
    val activeSessions: Int,
)
