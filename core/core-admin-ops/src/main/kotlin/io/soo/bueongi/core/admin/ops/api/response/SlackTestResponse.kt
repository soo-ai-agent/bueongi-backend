package io.soo.bueongi.core.admin.ops.api.response

data class SlackTestResponse(
    val sent: Boolean,
    val enabled: Boolean,
) {
    companion object {
        fun from(sent: Boolean, enabled: Boolean): SlackTestResponse {
            return SlackTestResponse(sent = sent, enabled = enabled)
        }
    }
}
