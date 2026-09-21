package io.soo.bueongi.core.route

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class RouteDependencyDirectionTest {

    @Test
    fun `안전 채점과 마커는 경로 본체를 참조하지 않는다`() {
        val violations: MutableList<String> = ArrayList()

        for (subPackage in INNER_PACKAGES) {
            for (source in sourcesUnder(subPackage)) {
                for (line in source.readLines()) {
                    if (!line.startsWith("import ")) {
                        continue
                    }
                    for (forbidden in FORBIDDEN_IMPORTS) {
                        if (line.contains(forbidden)) {
                            violations.add("${source.name}: ${line.trim()}")
                        }
                    }
                }
            }
        }

        assertThat(violations)
            .describedAs("domain/safety·domain/marker 는 경로 본체(api·domain/service·domain/model)를 참조하면 안 된다")
            .isEmpty()
    }

    @Test
    fun `검사 대상 파일이 실제로 존재한다 - 경로가 바뀌면 이 테스트가 조용히 통과하지 않게`() {
        for (subPackage in INNER_PACKAGES) {
            assertThat(sourcesUnder(subPackage))
                .describedAs("$subPackage 아래 소스가 있어야 한다")
                .isNotEmpty()
        }
    }

    private fun sourcesUnder(subPackage: String): List<File> {
        val root = File(MAIN_SOURCE_ROOT, subPackage)
        if (!root.isDirectory) {
            return emptyList()
        }
        val found: MutableList<File> = ArrayList()
        for (file in root.walkTopDown()) {
            if (file.isFile && file.extension == "kt") {
                found.add(file)
            }
        }
        return found
    }

    companion object {

        private const val MAIN_SOURCE_ROOT: String = "src/main/kotlin/io/soo/bueongi/core/route/domain"

        private val INNER_PACKAGES: List<String> = listOf("safety", "marker")

        private val FORBIDDEN_IMPORTS: List<String> = listOf(
            "io.soo.bueongi.core.route.api.",
            "io.soo.bueongi.core.route.domain.service.",
            "io.soo.bueongi.core.route.domain.model.",
            "io.soo.bueongi.core.route.domain.error.",
        )
    }
}
