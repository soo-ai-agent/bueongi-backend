package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.storage.db.core.facility.model.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.store.FacilityUploadStore
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.FacilityType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FacilityUploadStoreTest {

    private val mapper: ObjectMapper = ObjectMapper()

    @Test
    fun `save 는 파일을 만들고 findByType 이 필드까지 왕복 보존한다`(@TempDir tmp: Path) {
        val store = FacilityUploadStore(mapper, tmp.toFile().absolutePath)
        val points = listOf(
            FacilityPoint(FacilityType.CCTV, 37.5, 127.0, name = "c1", purpose = "생활방범", cameraCount = 4),
            FacilityPoint(FacilityType.CCTV, 37.51, 127.01, name = "c2", address = "서울 종로구", phone = "02-1"),
        )

        store.save(FacilityType.CCTV, points)

        assertThat(File(tmp.toFile(), "cctv.json")).exists()
        val loaded = store.findByType(FacilityType.CCTV)!!
        assertThat(loaded).hasSize(2)
        assertThat(loaded[0].name).isEqualTo("c1")
        assertThat(loaded[0].purpose).isEqualTo("생활방범")
        assertThat(loaded[0].cameraCount).isEqualTo(4)
        assertThat(loaded[1].address).isEqualTo("서울 종로구")
        assertThat(loaded[1].phone).isEqualTo("02-1")
    }

    @Test
    fun `findByType 은 파일이 없으면 null`(@TempDir tmp: Path) {
        val store = FacilityUploadStore(mapper, File(tmp.toFile(), "nope").absolutePath)
        assertThat(store.findByType(FacilityType.POLICE)).isNull()
    }

    @Test
    fun `save 는 없는 디렉터리를 만들고 재저장 시 덮어쓴다`(@TempDir tmp: Path) {
        val store = FacilityUploadStore(mapper, File(tmp.toFile(), "made/here").absolutePath)
        store.save(FacilityType.LAMP, listOf(FacilityPoint(FacilityType.LAMP, 37.5, 127.0)))
        store.save(FacilityType.LAMP, listOf(FacilityPoint(FacilityType.LAMP, 37.6, 127.1), FacilityPoint(FacilityType.LAMP, 37.7, 127.2)))

        assertThat(store.findByType(FacilityType.LAMP)).hasSize(2)
    }
}
