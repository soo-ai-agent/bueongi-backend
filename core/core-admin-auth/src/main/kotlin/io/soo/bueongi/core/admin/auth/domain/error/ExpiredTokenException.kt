package io.soo.bueongi.core.admin.auth.domain.error

class ExpiredTokenException(cause: Throwable? = null) : RuntimeException("access token expired", cause)
