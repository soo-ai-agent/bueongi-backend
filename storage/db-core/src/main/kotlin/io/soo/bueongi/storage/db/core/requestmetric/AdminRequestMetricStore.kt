package io.soo.bueongi.storage.db.core.requestmetric

// 사용자 창과 한 창을 쓰면 5초 폴링이 사용자 기록을 밀어내 "최근 N건"의 뜻이 달라진다.
interface AdminRequestMetricStore : RequestMetricStore
