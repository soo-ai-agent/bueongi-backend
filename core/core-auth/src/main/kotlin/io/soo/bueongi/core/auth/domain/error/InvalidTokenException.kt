package io.soo.bueongi.core.auth.domain.error

class InvalidTokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
