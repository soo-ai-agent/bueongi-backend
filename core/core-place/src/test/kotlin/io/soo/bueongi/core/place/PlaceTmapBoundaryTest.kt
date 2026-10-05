package io.soo.bueongi.core.place

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class PlaceTmapBoundaryTest {
    @Test
    fun `Tmap 을 아는 파일은 PlaceSearchService 하나뿐이다`() {
        val knowsTmap: MutableList<String> = ArrayList()
        for (source in mainSources()) {
            for (line in source.readLines()) {
                if (line.startsWith("import $TMAP_PACKAGE")) {
                    knowsTmap.add(source.name)
                    break
                }
            }
        }

        assertThat(knowsTmap)
            .describedAs("외부 응답 타입은 경계 한 곳에서 도메인 모델로 바꿔 올린다")
            .containsExactly("PlaceSearchService.kt")
    }

    @Test
    fun `검사 대상 파일이 실제로 존재한다 - 경로가 바뀌면 이 테스트가 조용히 통과하지 않게`() {
        assertThat(mainSources()).isNotEmpty()
    }

    private fun mainSources(): List<File> {
        val found: MutableList<File> = ArrayList()
        for (file in File(MAIN_SOURCE_ROOT).walkTopDown()) {
            if (file.isFile && file.extension == "kt") {
                found.add(file)
            }
        }
        return found
    }

    companion object {
        private const val MAIN_SOURCE_ROOT: String = "src/main/kotlin/io/soo/bueongi/core/place"
        private const val TMAP_PACKAGE: String = "io.soo.bueongi.client.tmap."
    }
}
