package io.soo.bueongi.support.util

/** 값을 자바스크립트 문자열 리터럴 안에 넣을 때 리터럴·태그를 탈출하지 못하게 만든다. */
object JsEscape {

    fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
}
