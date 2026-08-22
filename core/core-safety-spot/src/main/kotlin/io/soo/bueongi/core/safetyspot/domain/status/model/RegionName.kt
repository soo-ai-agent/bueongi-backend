package io.soo.bueongi.core.safetyspot.domain.status.model

/** 자치단체 이름. 원천이 말한 것만 담는다 — 추측해서 채우지 않는다. */
data class RegionName(
    /**
     * 지금 표는 230곳 전부 시도를 갖는다. 그래도 빈 문자열을 허용해 둔다 —
     * 새 자치단체 코드가 들어왔는데 원천이 시도를 말해 주지 않으면 채울 값이 없다.
     */
    val sido: String,
    val name: String,
)
