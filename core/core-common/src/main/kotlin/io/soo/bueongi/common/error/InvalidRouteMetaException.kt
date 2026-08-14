package io.soo.bueongi.common.error

class InvalidRouteMetaException(
    val field: String,
    override val message: String,
    val rejectedValue: Any?,
) : RuntimeException(message)
