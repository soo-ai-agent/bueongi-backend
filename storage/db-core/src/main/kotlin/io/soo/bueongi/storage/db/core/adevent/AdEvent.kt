package io.soo.bueongi.storage.db.core.adevent

/**
 * 광고 노출(호출) 1건의 기록. 관리자 페이지의 "광고 노출" 화면에 집계되어 노출된다 —
 * 언제([atEpochMs]) 어떤 지면([placement])의 광고가 호출됐는지.
 *
 * 수신 요청 메트릭([io.soo.bueongi.storage.db.core.requestmetric.RequestMetric])과 동일 철학의 얇은 계측 기록이다 —
 * PII 는 담지 않고(지면 종류만), 저장은 요청 처리의 순수 부수효과다.
 */
data class AdEvent(
    /** 노출 시각(epoch ms). 최근순 정렬·시간대/일자 집계용. */
    val atEpochMs: Long,
    /** 광고 지면: interstitial | native. 허용값 검증은 요청 DTO 계층에서 끝낸다(저장소는 유효 값만 받는다). */
    val placement: String,
)
