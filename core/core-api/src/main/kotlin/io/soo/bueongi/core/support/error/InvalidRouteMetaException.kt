package io.soo.bueongi.core.support.error

class InvalidRouteMetaException(
    val field: String,
    override val message: String,
    val rejectedValue: Any?,
) : RuntimeException(message)
