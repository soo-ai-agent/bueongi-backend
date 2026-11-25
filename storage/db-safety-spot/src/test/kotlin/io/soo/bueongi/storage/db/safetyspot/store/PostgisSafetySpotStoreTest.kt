package io.soo.bueongi.storage.db.safetyspot.store

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.sql.DriverManager
import java.time.Clock

// 실제 PostGIS 에 대고 돈다. 접속할 DB 는 BUEONGI_DB_URL·USERNAME·PASSWORD 로 받고, 닿지 않으면 건너뛴다.
@Tag("context")
class PostgisSafetySpotStoreTest {
    private lateinit var store: PostgisSafetySpotStore

    @BeforeEach
    fun setUp() {
        val url: String = System.getenv("BUEONGI_DB_URL") ?: "jdbc:postgresql://localhost:5432/bueongi"
        val username: String = System.getenv("BUEONGI_DB_USERNAME") ?: "bueongi"
        val password: String = System.getenv("BUEONGI_DB_PASSWORD") ?: "bueongi"
        assumeTrue(canConnect(url, username, password), "PostGIS 에 닿지 않아 건너뜀: $url")

        val dataSource = DriverManagerDataSource(url, username, password)
        Flyway.configure().dataSource(dataSource).outOfOrder(true).load().migrate()
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        jdbc.jdbcTemplate.execute("DELETE FROM safety_spot; DELETE FROM safety_spot_slice;")
        store =
            PostgisSafetySpotStore(
                jdbc = jdbc,
                transactions = TransactionTemplate(DataSourceTransactionManager(dataSource)),
                cdnFiles = SafetySpotCdnFiles("no-such-dir", ObjectMapper()),
                clock = Clock.systemUTC(),
            )
    }

    @Test
    fun `경로 30m 안의 시설만 찾는다`() {
        val path: List<GeoPoint> = listOf(GeoPoint(37.5000, 127.0000), GeoPoint(37.5000, 127.0100))
        val near: SafetySpotPoint = cctv(37.50020, 127.0050)
        val far: SafetySpotPoint = cctv(37.50040, 127.0050)
        store.replaceFiles(SafetySpotType.CCTV, listOf(near, far))

        val found: List<SafetySpotPoint> = store.findWithinCorridor(SafetySpotType.CCTV, path, 30.0)

        assertThat(found).containsExactly(near)
    }

    @Test
    fun `같은 좌표는 한 곳으로 세고 외부 API 출처를 남긴다`() {
        val center = GeoPoint(37.5, 127.0)
        store.replaceFiles(SafetySpotType.SECURITY_LAMP, listOf(lamp(37.5, 127.0, name = "업로드", region = "3000000")))
        store.replaceRegion(SafetySpotType.SECURITY_LAMP, "3110000", listOf(lamp(37.5, 127.0, name = "API", region = "3110000")))

        val found: List<SafetySpotPoint> = store.findWithinRadius(SafetySpotType.SECURITY_LAMP, center, 10.0)

        assertThat(found.map { it.name }).containsExactly("API")
    }

    @Test
    fun `업로드에 든 지역의 API 조각은 업로드할 때 지운다`() {
        store.replaceRegion(SafetySpotType.SECURITY_LAMP, "5340000", listOf(lamp(35.10, 128.0, name = "API", region = "5340000")))

        store.replaceFiles(SafetySpotType.SECURITY_LAMP, listOf(lamp(35.00, 128.0, name = "업로드", region = "5340000")))

        assertThat(store.findByRegion(SafetySpotType.SECURITY_LAMP, "5340000").map { it.name }).containsExactly("업로드")
    }

    private fun cctv(lat: Double, lng: Double): SafetySpotPoint {
        return SafetySpotPoint.of(SafetySpotType.CCTV, lat, lng)
    }

    private fun lamp(lat: Double, lng: Double, name: String, region: String): SafetySpotPoint {
        return SafetySpotPoint.of(SafetySpotType.SECURITY_LAMP, lat, lng, name = name, regionCode = region)
    }

    private fun canConnect(url: String, username: String, password: String): Boolean {
        return try {
            DriverManager.getConnection(url, username, password).use { true }
        } catch (e: Exception) {
            false
        }
    }
}
