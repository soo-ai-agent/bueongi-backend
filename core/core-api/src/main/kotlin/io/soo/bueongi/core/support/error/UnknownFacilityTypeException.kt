package io.soo.bueongi.core.support.error

class UnknownFacilityTypeException(
    val rejectedType: String,
    val knownTypes: List<String>,
) : RuntimeException("알 수 없는 시설 종류입니다: $rejectedType (허용: ${knownTypes.joinToString(", ")})")
