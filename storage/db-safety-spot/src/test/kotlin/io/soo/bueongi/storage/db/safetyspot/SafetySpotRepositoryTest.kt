package io.soo.bueongi.storage.db.safetyspot
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRegionCache
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRepository
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotUploadStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SafetySpotRepositoryTest {
    private val mapper = ObjectMapper()

    private fun write(dir: File, rel: String, json: String) {
        val file = File(dir, rel)
        file.parentFile.mkdirs()
        file.writeText(json)
    }

    private fun uploadStore(tmp: Path): SafetySpotUploadStore = SafetySpotUploadStore(mapper, File(tmp.toFile(), "uploads").absolutePath)

    @Test
    fun `CDN 컬렉션과 safepath 를 적재해 인덱스를 만든다`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.toFile()
        write(
            dir,
            "cctv/11680.json",
            """{"type":"cctv","items":[
                 {"name":"c1","lat":37.5,"lng":127.0,"sigunguCode":"11680"},
                 {"name":"c2","lat":37.501,"lng":127.001,"sigunguCode":"11680"}]}""",
        )
        write(
            dir,
            "safehouse/all.json",
            """{"type":"safehouse","items":[{"name":"s1","lat":37.5,"lng":127.0}]}""",
        )
        write(
            dir,
            "safepath/all.json",
            """{"type":"safepath","items":[
                 {"name":"sp1","coords":[{"lat":37.5,"lng":127.0},{"lat":37.5,"lng":127.01}]}]}""",
        )

        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp))
        repo.load()

        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(2)
        assertThat(repo.index(SafetySpotType.SAFEHOUSE).size).isEqualTo(1)
        assertThat(repo.index(SafetySpotType.BELL).size).isZero()
        assertThat(repo.safePaths).hasSize(1)
        assertThat(repo.safePaths.first().coords).hasSize(2)
    }

    @Test
    fun `safepath 가 단일 점이면 1점짜리 경로로 적재`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.toFile()
        write(dir, "safepath/all.json", """{"items":[{"name":"p","lat":37.5,"lng":127.0}]}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp))
        repo.load()
        assertThat(repo.safePaths).hasSize(1)
        assertThat(repo.safePaths.first().coords).containsExactly(GeoPoint(37.5, 127.0))
    }

    @Test
    fun `파싱 실패 파일은 건너뛰고 정상 파일만 적재`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.toFile()
        write(dir, "cctv/bad.json", "{ this is not json")
        write(dir, "cctv/good.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp))
        repo.load()
        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `비유효 좌표(범위 밖)는 제외`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.toFile()
        write(
            dir,
            "cctv/x.json",
            """{"items":[{"lat":37.5,"lng":127.0},{"lat":999.0,"lng":127.0},{"lat":37.5,"lng":null}]}""",
        )
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp))
        repo.load()
        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `manifest 에 version 이 없으면 신선도는 null 이다`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.toFile()
        write(dir, "manifest.json", """{"generatedAt":"2026-08-11T06:39:07Z"}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp))
        repo.load()
        assertThat(repo.freshness).isNull()
    }

    @Test
    fun `빈 디렉터리도 죽지 않고 빈 인덱스`(
        @TempDir tmp: Path,
    ) {
        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp))
        assertThatCode { repo.load() }.doesNotThrowAnyException()
        assertThat(repo.index(SafetySpotType.CCTV).size).isZero()
        assertThat(repo.safePaths).isEmpty()
    }

    @Test
    fun `파일 출처 교체는 해당 종류만 바꾸고 다른 종류·safepath 는 그대로다`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        write(dir, "police/all.json", """{"items":[{"name":"파출소","lat":37.51,"lng":127.01}]}""")
        write(dir, "safepath/all.json", """{"items":[{"name":"p","lat":37.5,"lng":127.0}]}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp))
        repo.load()

        repo.replaceFiles(
            SafetySpotType.CCTV,
            listOf(
                SafetySpotPoint.of(type = SafetySpotType.CCTV, lat = 37.6, lng = 127.1, name = "live1"),
                SafetySpotPoint.of(type = SafetySpotType.CCTV, lat = 37.61, lng = 127.11, name = "live2"),
            ),
        )

        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(2)
        assertThat(repo.index(SafetySpotType.POLICE).size).isEqualTo(1)
        assertThat(repo.safePaths).hasSize(1)
    }

    @Test
    fun `파일 출처 교체는 load 전(빈 인덱스)에도 동작한다`(
        @TempDir tmp: Path,
    ) {
        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp))

        repo.replaceFiles(SafetySpotType.BELL, listOf(SafetySpotPoint.of(type = SafetySpotType.BELL, lat = 37.5, lng = 127.0)))

        assertThat(repo.index(SafetySpotType.BELL).size).isEqualTo(1)
    }

    @Test
    fun `파일 출처를 바꿔도 API 로 받은 지역 데이터는 남는다`(
        @TempDir tmp: Path,
    ) {
        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp))
        repo.replaceRegion(SafetySpotType.SECURITY_LAMP, "3860000", listOf(lamp(37.5, 127.0, "API")))

        repo.replaceFiles(SafetySpotType.SECURITY_LAMP, listOf(lamp(37.6, 127.1, "업로드")))

        assertThat(repo.index(SafetySpotType.SECURITY_LAMP).all.map { it.name }).containsExactlyInAnyOrder("API", "업로드")
    }

    @Test
    fun `같은 지역을 다시 받으면 그 지역만 새 값으로 바뀐다`(
        @TempDir tmp: Path,
    ) {
        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp))
        repo.replaceRegion(SafetySpotType.SECURITY_LAMP, "3860000", listOf(lamp(37.5, 127.0, "옛값")))
        repo.replaceRegion(SafetySpotType.SECURITY_LAMP, "5410000", listOf(lamp(35.5, 128.0, "다른 지역")))

        repo.replaceRegion(SafetySpotType.SECURITY_LAMP, "3860000", listOf(lamp(37.5, 127.0, "새값")))

        assertThat(repo.index(SafetySpotType.SECURITY_LAMP).all.map { it.name }).containsExactlyInAnyOrder("새값", "다른 지역")
    }

    @Test
    fun `좌표가 겹치면 API 로 받은 점이 남는다`(
        @TempDir tmp: Path,
    ) {
        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp))
        repo.replaceFiles(SafetySpotType.SECURITY_LAMP, listOf(lamp(37.5, 127.0, "파일")))

        repo.replaceRegion(SafetySpotType.SECURITY_LAMP, "3860000", listOf(lamp(37.5, 127.0, "API")))

        assertThat(repo.index(SafetySpotType.SECURITY_LAMP).all.map { it.name }).containsExactly("API")
    }

    @Test
    fun `업로드가 있어도 기동 때 지역 캐시의 API 데이터를 함께 적재한다`(
        @TempDir tmp: Path,
    ) {
        val uploads: SafetySpotUploadStore = uploadStore(tmp)
        uploads.save(SafetySpotType.SECURITY_LAMP, listOf(lamp(37.6, 127.1, "업로드")))
        val cache = SafetySpotRegionCache(mapper, File(tmp.toFile(), "region-cache").absolutePath)
        cache.save(SafetySpotType.SECURITY_LAMP, "3860000", listOf(lamp(37.5, 127.0, "API")))
        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploads, cache)

        repo.load()

        assertThat(repo.index(SafetySpotType.SECURITY_LAMP).all.map { it.name }).containsExactlyInAnyOrder("API", "업로드")
    }

    private fun lamp(lat: Double, lng: Double, name: String): SafetySpotPoint =
        SafetySpotPoint.of(type = SafetySpotType.SECURITY_LAMP, lat = lat, lng = lng, name = name)

    @Test
    fun `CCTV CSV 설정이 비어 있으면 적재 비활성(JSON CCTV 만)`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp))
        assertThatCode { repo.load() }.doesNotThrowAnyException()
        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `업로드 파일이 있으면 부팅 시 해당 종류를 통째로 교체한다(업로드 우선)`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        write(dir, "police/all.json", """{"items":[{"name":"파출소","lat":37.51,"lng":127.01}]}""")
        val store = SafetySpotUploadStore(mapper, File(dir, "uploads").absolutePath)
        store.save(
            SafetySpotType.CCTV,
            listOf(
                SafetySpotPoint.of(SafetySpotType.CCTV, 37.6, 127.1, name = "u1"),
                SafetySpotPoint.of(SafetySpotType.CCTV, 37.61, 127.11, name = "u2"),
                SafetySpotPoint.of(SafetySpotType.CCTV, 37.62, 127.12, name = "u3"),
            ),
        )

        val repo = SafetySpotRepository(dir.absolutePath, mapper, store)
        repo.load()

        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(3)
        assertThat(repo.index(SafetySpotType.POLICE).size).isEqualTo(1)
    }

    @Test
    fun `CDN 컬렉션이 없어도 업로드분은 적재된다`(
        @TempDir tmp: Path,
    ) {
        val store = SafetySpotUploadStore(mapper, File(tmp.toFile(), "uploads").absolutePath)
        store.save(SafetySpotType.BELL, listOf(SafetySpotPoint.of(SafetySpotType.BELL, 37.5, 127.0)))

        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, store)
        repo.load()

        assertThat(repo.index(SafetySpotType.BELL).size).isEqualTo(1)
        assertThat(repo.index(SafetySpotType.CCTV).size).isZero()
    }

    @Test
    fun `서로 다른 종류를 동시에 교체해도 어느 쪽도 유실되지 않는다`(
        @TempDir tmp: Path,
    ) {
        val types: List<SafetySpotType> = SafetySpotType.entries
        val pool: ExecutorService = Executors.newFixedThreadPool(types.size)

        repeat(TRIALS) {
            val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp))
            repo.load()
            val start = CountDownLatch(1)
            val done = CountDownLatch(types.size)

            types.forEachIndexed { index, type ->
                pool.execute {
                    start.await()
                    // 인덱스가 같은 좌표를 하나로 세므로, 개수를 확인하려면 좌표를 달리해야 한다.
                    repo.replaceFiles(type, List(index + 1) { offset -> point(type, offset) })
                    done.countDown()
                }
            }
            start.countDown()
            done.await(10, TimeUnit.SECONDS)

            types.forEachIndexed { index, type ->
                assertThat(repo.index(type).size)
                    .describedAs("동시 교체 후 %s 유실", type)
                    .isEqualTo(index + 1)
            }
        }
        pool.shutdown()
    }

    private fun point(type: SafetySpotType, offset: Int = 0): SafetySpotPoint =
        SafetySpotPoint.of(type = type, lat = 37.5 + offset * 0.001, lng = 127.0)

    private companion object {
        const val TRIALS: Int = 300
    }
}
