package io.soo.bueongi.core.place.api.request

import io.soo.bueongi.core.place.domain.error.InvalidKeywordException

data class PlaceSearchRequest(
    private val keyword: String,
) {
    val searchKeyword: String = keyword.trim()

    init {
        if (searchKeyword.length > MAX_KEYWORD_LENGTH) {
            throw InvalidKeywordException(maxLength = MAX_KEYWORD_LENGTH, actualLength = searchKeyword.length)
        }
    }

    companion object {
        const val MAX_KEYWORD_LENGTH: Int = 100
    }
}
