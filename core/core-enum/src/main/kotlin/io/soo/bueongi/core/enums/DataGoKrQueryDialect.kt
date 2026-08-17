package io.soo.bueongi.core.enums

/**
 * data.go.kr 계열별 조건 문법. 섞어 쓰면 `resultCode 10` 으로 거부된다(실측).
 *
 * - CONDITION: 행안부 조회서비스(1741000 계열). `cond[필드::연산자]` 를 쓴다.
 * - FIELD: 표준데이터(tn_pubr 계열). 필드명을 그대로 쓰고 `cond[]` 는 거부한다.
 */
enum class DataGoKrQueryDialect {
    CONDITION,
    FIELD,
}
