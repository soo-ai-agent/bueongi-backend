package io.soo.bueongi.support.util

import java.nio.charset.Charset

/**
 * 공공데이터 CSV 의 인코딩. UTF-8 이 아니면 대개 이것이다.
 * 자바 표준 이름은 MS949 지만 원천 문서는 CP949 로 적혀 있어 이름을 그대로 쓴다.
 */
val CP949: Charset = Charset.forName("MS949")
