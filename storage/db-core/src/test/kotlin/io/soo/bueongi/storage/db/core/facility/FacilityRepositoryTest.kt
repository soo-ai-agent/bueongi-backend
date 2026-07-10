package io.soo.bueongi.storage.db.core.facility
import io.soo.bueongi.core.enums.FacilityType

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.Charset
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

        val repo = FacilityRepository(dir.absolutePath, mapper, "")
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
        val repo = FacilityRepository(dir.absolutePath, mapper, "")
        repo.load()
        assertThat(repo.safePaths).hasSize(1)
        assertThat(repo.safePaths.first().coords).containsExactly(GeoPoint(37.5, 127.0))
    }

    @Test
    fun `파싱 실패 파일은 건너뛰고 정상 파일만 적재`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/bad.json", "{ this is not json")
        write(dir, "cctv/good.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        val repo = FacilityRepository(dir.absolutePath, mapper, "")
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
        val repo = FacilityRepository(dir.absolutePath, mapper, "")
        repo.load()
        assertThat(repo.index(FacilityType.CCTV).size).isEqualTo(1)
    }

    @Test
    fun `빈 디렉터리도 죽지 않고 빈 인덱스`(@TempDir tmp: Path) {
        val repo = FacilityRepository(tmp.toFile().absolutePath, mapper, "")
        assertThatCode { repo.load() }.doesNotThrowAnyException()
        assertThat(repo.index(FacilityType.CCTV).size).isZero()
        assertThat(repo.safePaths).isEmpty()
    }

    @Test
    fun `CCTV CSV(CP949)를 JSON CCTV 와 합쳐 적재`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        // CP949(MS949)로 인코딩된 표준 18컬럼 CSV: 헤더 1 + 방범 정상행 2 + 좌표불량 1 + 비방범(교통단속) 1.
        val csv = File(dir, "CCTV정보.csv")
        // 후행 12컬럼(카메라대수~최종수정시점)으로 좌표/목적을 뒤 오프셋으로 읽는다.
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
                appendLine(row("id3", "bad", "생활방범")) // 좌표불량 → skip
                appendLine(row("id4", "37.56", "교통단속")) // 비방범 → 제외
            },
            Charset.forName("MS949"),
        )

        val repo = FacilityRepository(dir.absolutePath, mapper, csv.absolutePath)
        repo.load()

        // JSON 1 + CSV 방범 정상행 2 = 3 (좌표불량·비방범 행은 제외).
        assertThat(repo.index(FacilityType.CCTV).size).isEqualTo(3)
    }

    @Test
    fun `replace 는 해당 종류 인덱스만 원자 교체하고 다른 종류·safepath·신선도는 불변`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        write(dir, "police/all.json", """{"items":[{"name":"파출소","lat":37.51,"lng":127.01}]}""")
        write(dir, "safepath/all.json", """{"items":[{"name":"p","lat":37.5,"lng":127.0}]}""")
        val repo = FacilityRepository(dir.absolutePath, mapper, "")
        repo.load()

        val replaced = repo.replace(
            FacilityType.CCTV,
            listOf(
                FacilityPoint(type = FacilityType.CCTV, lat = 37.6, lng = 127.1, name = "live1"),
                FacilityPoint(type = FacilityType.CCTV, lat = 37.61, lng = 127.11, name = "live2"),
            ),
        )

        assertThat(replaced).isEqualTo(2)
        assertThat(repo.index(FacilityType.CCTV).size).isEqualTo(2) // 파일 1건 → 라이브 2건 교체
        assertThat(repo.index(FacilityType.POLICE).size).isEqualTo(1) // 다른 종류 불변
        assertThat(repo.safePaths).hasSize(1) // 안심귀갓길 불변
    }

    @Test
    fun `replace 는 load 전(빈 인덱스)에도 동작한다`(@TempDir tmp: Path) {
        val repo = FacilityRepository(tmp.toFile().absolutePath, mapper, "")

        repo.replace(FacilityType.BELL, listOf(FacilityPoint(type = FacilityType.BELL, lat = 37.5, lng = 127.0)))

        assertThat(repo.index(FacilityType.BELL).size).isEqualTo(1)
    }

    @Test
    fun `CCTV CSV 설정이 비어 있으면 적재 비활성(JSON CCTV 만)`(@TempDir tmp: Path) {
        val dir = tmp.toFile()
        write(dir, "cctv/seed.json", """{"items":[{"lat":37.5,"lng":127.0}]}""")
        val repo = FacilityRepository(dir.absolutePath, mapper, "")
        assertThatCode { repo.load() }.doesNotThrowAnyException()
        assertThat(repo.index(FacilityType.CCTV).size).isEqualTo(1)
    }
}
