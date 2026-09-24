package io.soo.bueongi.client.datagokr

import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient

const val TEST_SERVICE_KEY: String = "enc%2Ftest-key"

fun bindFetcher(key: String = TEST_SERVICE_KEY): Pair<DataGoKrPagedFetcher, MockRestServiceServer> {
    val builder: RestClient.Builder = RestClient.builder()
    val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    return DataGoKrPagedFetcher(serviceKey = key, restClientBuilder = builder) to server
}

fun envelope(items: String, totalCount: Int): String =
    """{"response":{"header":{"resultCode":"0","resultMsg":"정상"},"body":{"items":[$items],"totalCount":$totalCount}}}"""

fun standardEnvelope(item: String): String =
    """{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[$item]},"totalCount":1}}"""
