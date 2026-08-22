package io.soo.bueongi.storage.db.core.requestmetric

/**
 * 관리자 화면이 부른 요청만 담는 창. 사용자 창([RequestMetricStore])과 갈라 둔다 —
 * 한 창을 같이 쓰면 5초 폴링이 사용자 기록을 밀어내 "최근 N건"의 뜻이 달라진다.
 */
interface AdminRequestMetricStore : RequestMetricStore
