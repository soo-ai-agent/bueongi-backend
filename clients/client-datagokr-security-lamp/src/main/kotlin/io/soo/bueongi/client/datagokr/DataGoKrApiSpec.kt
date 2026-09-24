package io.soo.bueongi.client.datagokr

object DataGoKrApiSpec {
    // 표준데이터 계열(tn_pubr)은 2000 이상을 resultCode 10 으로 거부한다(실측).
    const val STANDARD_PAGE_SIZE: Int = 1000

    const val MAX_PAGES: Int = 5000

    // 대량 수집 중 간헐 503(SERVICETIMEOUT)이 나 최초 1 + 재시도 2회를 준다.
    const val MAX_ATTEMPTS_PER_PAGE: Int = 3

    const val RETRY_BASE_DELAY_MS: Long = 1000L

    val SUCCESS_CODES: Set<String> = setOf("0", "00")
}
