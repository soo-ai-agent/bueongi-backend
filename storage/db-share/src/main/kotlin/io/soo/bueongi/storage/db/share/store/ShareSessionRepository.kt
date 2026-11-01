package io.soo.bueongi.storage.db.share.store

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface ShareSessionRepository : JpaRepository<ShareSessionEntity, String> {
    fun findByExpiresAtAfter(now: Instant): List<ShareSessionEntity>

    fun countByExpiresAtAfter(now: Instant): Long

    @Transactional
    @Modifying
    @Query("delete from ShareSessionEntity s where s.expiresAt <= :now")
    fun deleteExpired(
        @Param("now") now: Instant,
    ): Int
}
