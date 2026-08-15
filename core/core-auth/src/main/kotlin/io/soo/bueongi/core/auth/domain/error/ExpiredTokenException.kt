package io.soo.bueongi.core.auth.domain.error

class ExpiredTokenException(cause: Throwable? = null) : RuntimeException("access token expired", cause)
