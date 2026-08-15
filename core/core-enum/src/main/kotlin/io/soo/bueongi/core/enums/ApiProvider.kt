package io.soo.bueongi.core.enums

/** 와이어·저장 문자열은 [name] 그대로(KAKAO|TMAP|DATAGOKR|SAFEMAP|SAFETYDATA) — 상수 이름을 바꾸면 계약이 깨진다. */
enum class ApiProvider {
    KAKAO,
    TMAP,
    DATAGOKR,
    SAFEMAP,
    SAFETYDATA,
}
