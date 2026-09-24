package io.soo.bueongi.client.datagokr

// 계열별 조건 문법 — 섞어 쓰면 resultCode 10 으로 거부된다(실측).
// CONDITION 은 1741000 계열의 `cond[필드::연산자]`, FIELD 는 tn_pubr 계열의 맨 필드명이다.
enum class DataGoKrQueryDialect {
    CONDITION,
    FIELD,
}
