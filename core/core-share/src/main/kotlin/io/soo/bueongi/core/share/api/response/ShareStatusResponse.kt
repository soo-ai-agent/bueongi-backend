package io.soo.bueongi.core.share.api.response

data class ShareStatusResponse(
    val activeSessions: Int,
) {
    companion object {
        fun from(activeSessions: Int): ShareStatusResponse {
            return ShareStatusResponse(activeSessions = activeSessions)
        }
    }
}
