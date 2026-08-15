package io.soo.bueongi.core.common.event

/**
 * 도메인이 "운영자가 알아야 할 일이 생겼다"를 알릴 때 발행하는 사건.
 *
 * 도메인은 **무슨 일이 있었는지만** 말하고, 그걸 Slack 으로 보낼지 이메일로 보낼지는 모른다.
 * 받는 쪽은 core-ops 다 — 도메인끼리 서로를 참조하지 않도록 사건 타입만 여기 공용 모듈에 둔다.
 *
 * 사건의 이름([key]·[title])은 그 사건을 아는 도메인의 발행기가 짓는다.
 */
data class OperationEvent(
    /** 같은 사건을 반복 발송하지 않기 위한 묶음 키. */
    val key: String,
    val title: String,
    val detail: String,
)
