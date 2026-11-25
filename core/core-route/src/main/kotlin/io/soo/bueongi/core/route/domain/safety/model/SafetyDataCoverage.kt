package io.soo.bueongi.core.route.domain.safety.model

data class SafetyDataCoverage(
    val covered: Set<SafetyItem>,
) {
    val missing: List<SafetyItem>
        get() {
            val missingItems: ArrayList<SafetyItem> = ArrayList()
            for (item in SafetyItem.entries) {
                if (item !in covered) {
                    missingItems.add(item)
                }
            }
            return missingItems
        }

    fun has(item: SafetyItem): Boolean {
        return item in covered
    }
}
