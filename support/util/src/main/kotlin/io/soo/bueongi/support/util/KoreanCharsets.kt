package io.soo.bueongi.support.util

import java.nio.charset.Charset

// 자바 표준 이름은 MS949 지만 공공데이터 원천 문서가 CP949 로 적어 그 이름을 쓴다.
val CP949: Charset = Charset.forName("MS949")
