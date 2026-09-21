package io.soo.bueongi.core.route.domain.safety.model

data class SafetyDataCoverage(val covered: Set<SafetyAxis>) {
    val missing: List<SafetyAxis>
        get() = SafetyAxis.entries.filter { it !in covered }

    fun has(axis: SafetyAxis): Boolean = axis in covered

    val isEmpty: Boolean
        get() = covered.isEmpty()
}
