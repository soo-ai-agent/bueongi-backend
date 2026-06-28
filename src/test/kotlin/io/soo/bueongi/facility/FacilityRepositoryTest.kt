package io.soo.bueongi.facility

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * [FacilityRepository] 적재 테스트. CDN 컬렉션(type 별 하위폴더)·safepath 좌표열 파싱과,
 * 일부 파일 누락/파싱 실패·빈 디렉터리에도 죽지 않는 회복력을 고정한다.
 */
class FacilityRepositoryTest {

    private val mapper = ObjectMapper()

    private fun write(dir: File, rel: String, json: String) {
        val file = File(dir, rel)
        file.parentFile.mkdirs()
        file.writeText(json)
    }

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

        val repo = FacilityRepository(dir.absolutePath, mapper)
        repo.load()

        assertThat(repo.index(FacilityType.CCTV).size).isEqualTo(2)
        assertThat(repo.index(FacilityType.SAFEHOUSE).size).isEqualTo(1)
        assertThat(repo.index(FacilityType.BELL).size).isZero() // 미적재 종류 → 빈 인덱스
        assertThat(repo.safePaths).hasSize(1)
        assertThat(repo.safePaths.first().coords).hasSize(2)
    }

    @Test
    fun `safepath 가 단일 점이면 1점짜리 경로로 적재`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "safepath/all.json", """{"items":[{"name":"p","lat":37.5,"lng":127.0}]}""")
        val repo = FacilityRepository(dir.absolutePath, mapper)
        repo.load()
        assertThat(repo.safePaths).hasSize(1)
        assertThat(repo.safePaths.first().coords).containsExactly(GeoPoint(37.5, 127.0))
    }

    @Test
    fun `파싱 실패 파일은 건너뛰고 정상 파일만 적재`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/bad.json", "{ this is not json")
        write(dir, "cctv/good.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        val repo = FacilityRepository(dir.absolutePath, mapper)
        repo.load()
        assertThat(repo.index(FacilityType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `비유효 좌표(범위 밖)는 제외`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(
            dir, "cctv/x.json",
            """{"items":[{"lat":37.5,"lng":127.0},{"lat":999.0,"lng":127.0},{"lat":37.5,"lng":null}]}""",
        )
        val repo = FacilityRepository(dir.absolutePath, mapper)
        repo.load()
        assertThat(repo.index(FacilityType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `빈 디렉터리도 죽지 않고 빈 인덱스`(@TempDir tmp: Path) {
        val repo = FacilityRepository(tmp.toFile().absolutePath, mapper)
        assertThatCode { repo.load() }.doesNotThrowAnyException()
        assertThat(repo.index(FacilityType.CCTV).size).isZero()
        assertThat(repo.safePaths).isEmpty()
    }
}
