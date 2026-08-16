package io.soo.bueongi.support.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PercentilesTest {

    @Test
    fun `빈 목록이면 어떤 분위수든 0 이다`() {
        assertThat(nearestRankPercentile(emptyList(), 50)).isEqualTo(0L)
        assertThat(nearestRankPercentile(emptyList(), 95)).isEqualTo(0L)
        assertThat(nearestRankPercentile(emptyList(), 99)).isEqualTo(0L)
    }

    @Test
    fun `단일 원소면 모든 분위수가 그 값이다`() {
        val single = listOf(42L)
        assertThat(nearestRankPercentile(single, 50)).isEqualTo(42L)
        assertThat(nearestRankPercentile(single, 95)).isEqualTo(42L)
        assertThat(nearestRankPercentile(single, 99)).isEqualTo(42L)
    }

    @Test
    fun `세 원소의 p50 은 중앙값 p95 p99 는 최댓값이다`() {
        val values = listOf(10L, 20L, 30L)
        assertThat(nearestRankPercentile(values, 50)).isEqualTo(20L)
        assertThat(nearestRankPercentile(values, 95)).isEqualTo(30L)
        assertThat(nearestRankPercentile(values, 99)).isEqualTo(30L)
    }

    @Test
    fun `열 원소의 분위수는 nearest-rank 순위의 표본값이다`() {
        val values = (1..10).map { (it * 10).toLong() }
        assertThat(nearestRankPercentile(values, 50)).isEqualTo(50L)
        assertThat(nearestRankPercentile(values, 90)).isEqualTo(90L)
        assertThat(nearestRankPercentile(values, 95)).isEqualTo(100L)
        assertThat(nearestRankPercentile(values, 99)).isEqualTo(100L)
    }
}
