package io.soo.bueongi.core.auth.domain

class ExpiredTokenException(cause: Throwable? = null) : RuntimeException("access token expired", cause)
