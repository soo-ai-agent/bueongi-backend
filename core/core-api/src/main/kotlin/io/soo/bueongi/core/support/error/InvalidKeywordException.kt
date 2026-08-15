package io.soo.bueongi.core.support.error

class InvalidKeywordException(
    val maxLength: Int,
    val actualLength: Int,
) : RuntimeException("검색어가 최대 길이(${maxLength}자)를 초과했습니다. (입력 ${actualLength}자)")
