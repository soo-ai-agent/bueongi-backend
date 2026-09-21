package io.soo.bueongi.storage.db.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryRecentLogTest {
    @Test
    fun `push 후 readRecent 는 최신이 먼저다`() {
        val log = InMemoryRecentLog<Long>(cap = 10)
        log.push(1L)
        log.push(2L)
        log.push(3L)

        assertEquals(listOf(3L, 2L, 1L), log.readRecent(10))
    }

    @Test
    fun `limit 만큼만 반환한다`() {
        val log = InMemoryRecentLog<Long>(cap = 10)
        (1L..5L).forEach { log.push(it) }

        assertEquals(listOf(5L, 4L), log.readRecent(2))
    }

    @Test
    fun `상한을 넘으면 가장 오래된 것부터 축출된다`() {
        val log = InMemoryRecentLog<Long>(cap = 3)
        (1L..5L).forEach { log.push(it) }

        assertEquals(listOf(5L, 4L, 3L), log.readRecent(10))
    }

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertTrue(InMemoryRecentLog<Long>(cap = 10).readRecent(10).isEmpty())
    }
}
