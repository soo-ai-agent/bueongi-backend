package io.soo.bueongi.storage.db.share

import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredCoord
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.model.StoredRouteDestination
import io.soo.bueongi.storage.db.share.store.JpaLocationStore
import io.soo.bueongi.storage.db.share.store.ShareSessionEntity
import io.soo.bueongi.storage.db.share.store.ShareSessionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.Instant

@DataJpaTest
class JpaLocationStoreTest
    @Autowired
    constructor(
        private val repository: ShareSessionRepository,
    ) {
        private val clock = MutableTestClock()

        private val store = JpaLocationStore(repository, clock)

        private fun session(token: String, expiresAt: Instant): ShareSession {
            return ShareSession(
                token = token,
                ownerSecret = "sek",
                createdAt = clock.now,
                expiresAt = expiresAt,
            )
        }

        @Test
        fun `backend 라벨은 db`() {
            assertThat(store.backend).isEqualTo("db")
        }

        @Test
        fun `저장한 공유를 위치·경로·예정 도착까지 그대로 회수한다(왕복)`() {
            val route =
                StoredRoute(
                    destination = StoredRouteDestination(name = "집", lat = 37.56, lng = 126.97),
                    path = listOf(StoredCoord(37.5, 127.0)),
                    markers = emptyList(),
                    totalDistanceMeters = 1234,
                    totalTimeSeconds = 900,
                    type = "safe",
                )
            val saved =
                session("tok-1", clock.now.plusSeconds(3600)).copy(
                    location =
                        StoredLocation(
                            lat = 37.5,
                            lng = 127.04,
                            updatedAt = clock.now,
                            remainingMeters = 500.0,
                            remainingSeconds = 360.0,
                        ),
                    route = route,
                    expectedArrivalAt = clock.now.plusSeconds(600),
                )

            store.save(saved)

            assertThat(store.findByToken("tok-1")).isEqualTo(saved)
        }

        @Test
        fun `없는 token 은 null`() {
            assertThat(store.findByToken("nope")).isNull()
        }

        @Test
        fun `만료가 지나면 find 는 null 이다`() {
            store.save(session("tok-1", clock.now.plusSeconds(60)))
            clock.advanceSeconds(61)
            assertThat(store.findByToken("tok-1")).isNull()
        }

        @Test
        fun `save 는 같은 token 을 덮어쓴다(위치 갱신)`() {
            val created = session("tok-1", clock.now.plusSeconds(3600))
            store.save(created)
            val updated = created.copy(location = StoredLocation(lat = 37.51, lng = 127.05, updatedAt = clock.now))

            store.save(updated)

            assertThat(store.findByToken("tok-1")).isEqualTo(updated)
        }

        @Test
        fun `save 로 덮어써도 보호자 열람 기록은 보존된다`() {
            val created = session("tok-1", clock.now.plusSeconds(3600))
            store.save(created)
            store.markViewed("tok-1")

            store.save(created.copy(location = StoredLocation(lat = 37.51, lng = 127.05, updatedAt = clock.now)))

            assertThat(store.isWatched("tok-1")).isTrue()
        }

        @Test
        fun `delete 는 공유를 즉시 제거한다(도착·중단)`() {
            store.save(session("tok-1", clock.now.plusSeconds(3600)))
            store.deleteByToken("tok-1")
            assertThat(store.findByToken("tok-1")).isNull()
        }

        @Test
        fun `markViewed 후 isWatched 는 true, 유효시간(12초) 지나면 false`() {
            store.save(session("tok-1", clock.now.plusSeconds(3600)))
            store.markViewed("tok-1")
            assertThat(store.isWatched("tok-1")).isTrue()
            clock.advanceSeconds(13)
            assertThat(store.isWatched("tok-1")).isFalse()
        }

        @Test
        fun `조회 기록이 없으면 isWatched 는 false`() {
            store.save(session("tok-1", clock.now.plusSeconds(3600)))
            assertThat(store.isWatched("tok-1")).isFalse()
        }

        @Test
        fun `활성 집계는 만료를 제외한다`() {
            store.save(session("tok-live", clock.now.plusSeconds(3600)))
            store.save(session("tok-dead", clock.now.plusSeconds(60)))
            clock.advanceSeconds(61)

            assertThat(store.countActiveSessions()).isEqualTo(1)
            assertThat(store.findActiveSessions().map { it.token }).containsExactly("tok-live")
        }

        @Test
        fun `deleteExpired 는 만료 행을 물리적으로 지운다`() {
            store.save(session("tok-dead", clock.now.plusSeconds(60)))
            clock.advanceSeconds(61)

            store.deleteExpired()

            assertThat(repository.count()).isEqualTo(0)
        }

        @Test
        fun `경로 JSON 이 깨져도 공유는 살고 경로만 비운다`() {
            repository.save(
                ShareSessionEntity(
                    token = "tok-1",
                    ownerSecret = "sek",
                    createdAt = clock.now,
                    expiresAt = clock.now.plusSeconds(3600),
                    routeJson = "{broken",
                ),
            )

            val found: ShareSession? = store.findByToken("tok-1")

            assertThat(found).isNotNull()
            assertThat(found!!.route).isNull()
        }
    }
