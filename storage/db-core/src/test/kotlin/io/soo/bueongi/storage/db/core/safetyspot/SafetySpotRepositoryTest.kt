package io.soo.bueongi.storage.db.core.safetyspot
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRepository
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotUploadStore
import io.soo.bueongi.core.enums.SafetySpotType

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.Charset
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

    private fun uploadStore(tmp: Path): SafetySpotUploadStore =
        SafetySpotUploadStore(mapper, File(tmp.toFile(), "uploads").absolutePath)

    @Test
    fun `CDN 컬렉션과 safepath 를 적재해 인덱스를 만든다`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(
            dir, "cctv/11680.json",
            """{"type":"cctv","items":[
                 {"name":"c1","lat":37.5,"lng":127.0,"sigunguCode":"11680"},
                 {"name":"c2","lat":37.501,"lng":127.001,"sigunguCode":"11680"}]}""",
        )
        write(
            dir, "safehouse/all.json",
            """{"type":"safehouse","items":[{"name":"s1","lat":37.5,"lng":127.0}]}""",
        )
        write(
            dir, "safepath/all.json",
            """{"type":"safepath","items":[
                 {"name":"sp1","coords":[{"lat":37.5,"lng":127.0},{"lat":37.5,"lng":127.01}]}]}""",
        )

        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp), "")
        repo.load()

        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(2)
        assertThat(repo.index(SafetySpotType.SAFEHOUSE).size).isEqualTo(1)
        assertThat(repo.index(SafetySpotType.BELL).size).isZero()
        assertThat(repo.safePaths).hasSize(1)
        assertThat(repo.safePaths.first().coords).hasSize(2)
    }

    @Test
    fun `safepath 가 단일 점이면 1점짜리 경로로 적재`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "safepath/all.json", """{"items":[{"name":"p","lat":37.5,"lng":127.0}]}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp), "")
        repo.load()
        assertThat(repo.safePaths).hasSize(1)
        assertThat(repo.safePaths.first().coords).containsExactly(GeoPoint(37.5, 127.0))
    }

    @Test
    fun `파싱 실패 파일은 건너뛰고 정상 파일만 적재`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/bad.json", "{ this is not json")
        write(dir, "cctv/good.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp), "")
        repo.load()
        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `비유효 좌표(범위 밖)는 제외`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(
            dir, "cctv/x.json",
            """{"items":[{"lat":37.5,"lng":127.0},{"lat":999.0,"lng":127.0},{"lat":37.5,"lng":null}]}""",
        )
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp), "")
        repo.load()
        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `빈 디렉터리도 죽지 않고 빈 인덱스`(@TempDir tmp: Path) {
        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp), "")
        assertThatCode { repo.load() }.doesNotThrowAnyException()
        assertThat(repo.index(SafetySpotType.CCTV).size).isZero()
        assertThat(repo.safePaths).isEmpty()
    }

    @Test
    fun `CP949 CSV 를 JSON CCTV 와 합쳐 적재`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        val csv = File(dir, "CCTV정보.csv")
        fun row(id: String, lat: String, purpose: String) =
            "3000000,$id,서울 종로구청,종로 $id,,$purpose,2,200,360도 전방면,30,202005," +
                "02-0000-0000,$lat,126.97,2026-05-18,,갱신,수정"
        csv.writeText(
            buildString {
                appendLine("개방자치단체코드,관리번호,관리기관명,소재지도로명주소,소재지지번주소,설치목적구분," +
                    "카메라대수,카메라화소수,촬영방면정보,보관일수,설치연월,관리기관전화번호," +
                    "WGS84위도,WGS84경도,데이터기준일자,데이터갱신구분,데이터갱신시점,최종수정시점")
                appendLine(row("id1", "37.58", "생활방범"))
                appendLine(row("id2", "37.57", "어린이보호"))
                appendLine(row("id3", "bad", "생활방범"))
                appendLine(row("id4", "37.56", "교통단속"))
            },
            Charset.forName("MS949"),
        )

        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp), csv.absolutePath)
        repo.load()

        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(3)
    }

    @Test
    fun `replace 는 해당 종류 인덱스만 원자 교체하고 다른 종류·safepath·신선도는 불변`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        write(dir, "police/all.json", """{"items":[{"name":"파출소","lat":37.51,"lng":127.01}]}""")
        write(dir, "safepath/all.json", """{"items":[{"name":"p","lat":37.5,"lng":127.0}]}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp), "")
        repo.load()

        val replaced = repo.replace(
            SafetySpotType.CCTV,
            listOf(
                SafetySpotPoint(type = SafetySpotType.CCTV, lat = 37.6, lng = 127.1, name = "live1"),
                SafetySpotPoint(type = SafetySpotType.CCTV, lat = 37.61, lng = 127.11, name = "live2"),
            ),
        )

        assertThat(replaced).isEqualTo(2)
        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(2)
        assertThat(repo.index(SafetySpotType.POLICE).size).isEqualTo(1)
        assertThat(repo.safePaths).hasSize(1)
    }

    @Test
    fun `replace 는 load 전(빈 인덱스)에도 동작한다`(@TempDir tmp: Path) {
        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp), "")

        repo.replace(SafetySpotType.BELL, listOf(SafetySpotPoint(type = SafetySpotType.BELL, lat = 37.5, lng = 127.0)))

        assertThat(repo.index(SafetySpotType.BELL).size).isEqualTo(1)
    }

    @Test
    fun `CCTV CSV 설정이 비어 있으면 적재 비활성(JSON CCTV 만)`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        val repo = SafetySpotRepository(dir.absolutePath, mapper, uploadStore(tmp), "")
        assertThatCode { repo.load() }.doesNotThrowAnyException()
        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `업로드 파일이 있으면 부팅 시 해당 종류를 통째로 교체한다(업로드 우선)`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        write(dir, "police/all.json", """{"items":[{"name":"파출소","lat":37.51,"lng":127.01}]}""")
        val store = SafetySpotUploadStore(mapper, File(dir, "uploads").absolutePath)
        store.save(
            SafetySpotType.CCTV,
            listOf(
                SafetySpotPoint(SafetySpotType.CCTV, 37.6, 127.1, name = "u1"),
                SafetySpotPoint(SafetySpotType.CCTV, 37.61, 127.11, name = "u2"),
                SafetySpotPoint(SafetySpotType.CCTV, 37.62, 127.12, name = "u3"),
            ),
        )

        val repo = SafetySpotRepository(dir.absolutePath, mapper, store, "")
        repo.load()

        assertThat(repo.index(SafetySpotType.CCTV).size).isEqualTo(3)
        assertThat(repo.index(SafetySpotType.POLICE).size).isEqualTo(1)
    }

    @Test
    fun `CDN 컬렉션이 없어도 업로드분은 적재된다`(@TempDir tmp: Path) {
        val store = SafetySpotUploadStore(mapper, File(tmp.toFile(), "uploads").absolutePath)
        store.save(SafetySpotType.BELL, listOf(SafetySpotPoint(SafetySpotType.BELL, 37.5, 127.0)))

        val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, store, "")
        repo.load()

        assertThat(repo.index(SafetySpotType.BELL).size).isEqualTo(1)
        assertThat(repo.index(SafetySpotType.CCTV).size).isZero()
    }

    @Test
    fun `서로 다른 종류를 동시에 교체해도 어느 쪽도 유실되지 않는다`(@TempDir tmp: Path) {
        val types: List<SafetySpotType> = SafetySpotType.entries
        val pool: ExecutorService = Executors.newFixedThreadPool(types.size)

        repeat(TRIALS) {
            val repo = SafetySpotRepository(tmp.toFile().absolutePath, mapper, uploadStore(tmp), "")
            repo.load()
            val start = CountDownLatch(1)
            val done = CountDownLatch(types.size)

            types.forEachIndexed { index, type ->
                pool.execute {
                    start.await()
                    // 인덱스가 같은 좌표를 하나로 세므로, 개수를 확인하려면 좌표를 달리해야 한다.
                    repo.replace(type, List(index + 1) { offset -> point(type, offset) })
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
        SafetySpotPoint(type = type, lat = 37.5 + offset * 0.001, lng = 127.0)

    private companion object {
        const val TRIALS: Int = 300
    }
}
