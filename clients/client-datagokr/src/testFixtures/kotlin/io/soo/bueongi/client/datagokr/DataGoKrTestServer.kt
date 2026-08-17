package io.soo.bueongi.client.datagokr

import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient

/** 자원별 클라이언트 모듈이 공유하는 테스트 하네스 — 모듈마다 복제하지 않는다. */
const val TEST_SERVICE_KEY: String = "enc%2Ftest-key"

fun bindFetcher(key: String = TEST_SERVICE_KEY): Pair<DataGoKrPagedFetcher, MockRestServiceServer> {
    val builder: RestClient.Builder = RestClient.builder()
    val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    return DataGoKrPagedFetcher(serviceKey = key, restClientBuilder = builder) to server
}

/** 1741000 계열 봉투(response 로 한 겹 더 감싼 형태). */
fun envelope(items: String, totalCount: Int): String =
    """{"response":{"header":{"resultCode":"0","resultMsg":"정상"},"body":{"items":[$items],"totalCount":$totalCount}}}"""

/** 표준데이터 계열 봉투(response 없이 header/body 가 최상위, items 는 item 래퍼). */
fun standardEnvelope(item: String): String =
    """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[$item]},"totalCount":1}}"""
