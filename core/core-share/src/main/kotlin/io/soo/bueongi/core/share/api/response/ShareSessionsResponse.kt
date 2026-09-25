package io.soo.bueongi.core.share.api.response

import io.soo.bueongi.core.share.domain.model.ActiveShareView

data class ShareSessionsResponse(val sessions: List<ActiveShareView>)
