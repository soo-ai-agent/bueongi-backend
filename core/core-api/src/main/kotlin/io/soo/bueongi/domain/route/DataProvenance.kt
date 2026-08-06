package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.GeoPoint

/**
 * 데이터 출처/기준일. kind='live' 는 실 공공데이터 기반 계산을 뜻하고,
 * 폴백(키 미설정/Tmap 전부 실패) 응답에는 이 필드 자체가 없다 — 프론트는 부재를 '예시 데이터'로 표기한다.
 */
data class DataProvenance(
    /** 'live' 고정(실데이터). 폴백은 provenance 자체가 null. */
    val kind: String,
    /** 시설 데이터 기준일 "YYYY.MM"(manifest generatedAt). 미상이면 null(표기 생략). */
    val basedOn: String? = null,
    /** 원천 표기(예: "공공데이터포털·서울 열린데이터광장"). */
    val origin: String,
)
