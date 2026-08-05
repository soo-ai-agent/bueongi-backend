package io.soo.bueongi.storage.db.core.apilog

import io.soo.bueongi.core.enums.ApiProvider

/**
 * 외부 API 호출 1건의 계측 기록. 관리자 페이지의 "외부 API 접속 내역"에 그대로 노출된다 —
 * 어떤 원천([provider])의 어떤 종류 호출([endpoint])이 언제([atEpochMs]) 성공/실패했고([success]) 얼마나 걸렸는지([durationMs]).
 * PII 는 담지 않는다(검색어·좌표·토큰 등 사용자 입력은 [detail] 에도 넣지 않는다 — 원인/비고만).
 */
data class ApiCallLog(
    /** 호출 시작 시각(epoch ms). 최근순 정렬·표시용. */
    val atEpochMs: Long,
    /** 원천 식별자 — [ApiProvider] 의 이름 문자열(KAKAO|TMAP|DATAGOKR|SAFEMAP|SAFETYDATA). 저장 포맷이라 String 으로 둔다. */
    val provider: String,
    /** 호출 종류(원천 내 구분): 예 cctv/bell/safehouse/police/route/search. */
    val endpoint: String,
    val success: Boolean,
    val durationMs: Long,
    /** 실패 사유/비고(성공이면 보통 null). 사용자 입력은 담지 않는다. */
    val detail: String? = null,
)
