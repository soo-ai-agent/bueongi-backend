package io.soo.bueongi.support.error

class InvalidRouteMetaException(
    val field: String,
    override val message: String,
    val rejectedValue: Any?,
) : RuntimeException(message)
