package io.soo.bueongi.core.safetyspot.domain.status.model

/** 자치단체 이름. 원천이 말한 것만 담는다 — 추측해서 채우지 않는다. */
data class RegionName(
    /** 원천 기관명이 시도를 밝히지 않은 자치단체가 34곳 있어 빈 문자열이 가능하다. */
    val sido: String,
    val name: String,
)
