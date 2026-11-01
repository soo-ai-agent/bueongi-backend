package io.soo.bueongi.core.route.domain.safety.model

data class SafetyDataCoverage(
    val covered: Set<SafetyItem>,
) {
    val missing: List<SafetyItem>
        get() = SafetyItem.entries.filter { it !in covered }

    fun has(item: SafetyItem): Boolean {
        return item in covered
    }
}
