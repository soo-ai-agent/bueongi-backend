package io.soo.bueongi.core.admin.api.auth

class ExpiredTokenException(cause: Throwable? = null) : RuntimeException("access token expired", cause)
