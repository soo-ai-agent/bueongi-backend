package io.soo.bueongi.client.datagokr

import io.soo.bueongi.core.enums.DataGoKrQueryDialect

/**
 * 수집 범위를 좁히는 조건. 아무것도 주지 않으면 전량 수집이라 일일 쿼터를 크게 먹는다
 * (CCTV 전국 37만 건 = 3,773회 / 쿼터 1,000회).
 *
 * 원천 규격은 `cond[<필드>::<연산자>]` 형태다.
 */
data class DataGoKrQuery(
    // 전국을 받을 때는 자치단체를 지정하지 않아 null 이 가능함
    val municipalityCode: String? = null,
    // 첫 수집에는 비교할 이전 시각이 없어 null 이 가능함
    val updatedSince: DataGoKrUpdatePoint? = null,
) {
    internal fun toQueryParams(dialect: DataGoKrQueryDialect): String {
        val params: StringBuilder = StringBuilder()
        if (municipalityCode != null) {
            if (dialect == DataGoKrQueryDialect.CONDITION) {
                params.append("&").append(condition(FIELD_MUNICIPALITY, OPERATOR_EQUALS, municipalityCode))
            } else {
                params.append("&").append(PARAM_INSTITUTION_CODE).append("=").append(municipalityCode)
            }
        }
        if (updatedSince != null && dialect == DataGoKrQueryDialect.CONDITION) {
            // 표준데이터 계열에는 갱신시점 필터가 없다(cond[] 자체를 거부) — 조용히 생략한다.
            params.append("&").append(condition(FIELD_UPDATED_AT, OPERATOR_GREATER_OR_EQUAL, updatedSince.value))
        }
        return params.toString()
    }

    /** 대괄호는 질의 문자열에 그대로 못 들어가 퍼센트 인코딩한다(`::` 는 허용 문자라 그대로 둔다). */
    private fun condition(field: String, operator: String, value: String): String =
        "cond%5B$field::$operator%5D=$value"

    private companion object {
        const val FIELD_MUNICIPALITY: String = "OPN_ATMY_GRP_CD"
        const val FIELD_UPDATED_AT: String = "DAT_UPDT_PNT"
        const val OPERATOR_EQUALS: String = "EQ"
        const val OPERATOR_GREATER_OR_EQUAL: String = "GTE"
        const val PARAM_INSTITUTION_CODE: String = "insttCode"
    }
}
