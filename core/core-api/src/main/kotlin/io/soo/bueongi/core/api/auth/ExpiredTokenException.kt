package io.soo.bueongi.core.api.auth

class ExpiredTokenException(cause: Throwable? = null) : RuntimeException("access token expired", cause)
