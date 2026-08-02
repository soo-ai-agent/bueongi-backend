package io.soo.bueongi.core.enums

/**
 * 외부 API 원천 — 계측(접속 내역 기록)·상태 요약·쿼터가 공유하는 **닫힌 어휘**.
 * 원천 이름이 원시 문자열("KAKAO" …)로 여러 파일에 흩어지면 오타가 조용히 통계에서 빠지므로
 * (계측은 부수효과라 아무도 실패하지 않는다) 컴파일러가 잡도록 enum 으로 고정한다([FacilityType] 과 같은 이유).
 *
 * 와이어·저장 문자열은 [name] 그대로다 — 접속 내역(`ApiCallLog.provider`)과 관리자 응답(`providers[].provider`)이
 * 기존과 바이트 동일하게 KAKAO|TMAP|DATAGOKR|SAFEMAP|SAFETYDATA 를 낸다. 따라서 **상수 이름을 바꾸면 계약이 깨진다**.
 */
enum class ApiProvider {
    /** Kakao Local — 장소 검색. */
    KAKAO,

    /** Tmap — 보행자 경로. */
    TMAP,

    /** 공공데이터포털(행안부 조회서비스·표준데이터) — CCTV·비상벨·여성안심지킴이집. 일 1000건 쿼터. */
    DATAGOKR,

    /** 생활안전지도(경찰청) — 치안시설. */
    SAFEMAP,

    /** 재난안전데이터 공유 플랫폼(행안부) — 가로등 공통POI. */
    SAFETYDATA,
}
