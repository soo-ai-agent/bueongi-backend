package io.soo.bueongi.storage.db.core

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.StringRedisTemplate

class RedisJsonLogTest {
    data class Item(val name: String, val count: Int)

    private val listOps: ListOperations<String, String> = mock()

    private val redis: StringRedisTemplate = mock<StringRedisTemplate>().also {
        whenever(it.opsForList()).thenReturn(listOps)
    }

    private fun log(): RedisJsonLog<Item> = RedisJsonLog(
        redis = redis,
        objectMapper = jacksonObjectMapper(),
        key = "testkey",
        cap = 10,
        type = Item::class.java,
        label = "테스트 기록",
    )

    @Test
    fun `push 는 JSON 으로 넣고 상한 만큼만 남기도록 자른다`() {
        log().push(Item(name = "가", count = 1))

        verify(listOps).leftPush("testkey", """{"name":"가","count":1}""")
        verify(listOps).trim("testkey", 0, 9)
    }

    @Test
    fun `깨진 항목은 건너뛰고 나머지는 살린다`() {
        whenever(listOps.range("testkey", 0, -1))
            .thenReturn(listOf("""{"name":"나","count":2}""", "깨진 JSON", """{"name":"다","count":3}"""))

        assertThat(log().readRecent()).containsExactly(Item("나", 2), Item("다", 3))
    }

    @Test
    fun `기록 중 Redis 가 죽어도 예외를 밖으로 내지 않는다`() {
        whenever(listOps.leftPush(any(), any())).thenThrow(RuntimeException("connection refused"))

        assertThatCode { log().push(Item("라", 4)) }.doesNotThrowAnyException()
    }

    @Test
    fun `조회 중 Redis 가 죽으면 빈 목록으로 돌린다`() {
        whenever(listOps.range(eq("testkey"), any(), any())).thenThrow(RuntimeException("connection refused"))

        assertThat(log().readRecent()).isEmpty()
    }
}
