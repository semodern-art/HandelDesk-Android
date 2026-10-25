package de.handeltheke.handeldesk.core

data class ReplacementPreview(
    val before: String,
    val after: String,
    val replacements: Int,
)

object DonorTools {
    fun replaceText(source: String, oldValue: String, newValue: String, ignoreCase: Boolean = true): ReplacementPreview {
        if (oldValue.isBlank() || source.isEmpty()) return ReplacementPreview(source, source, 0)
        val regex = Regex(Regex.escape(oldValue), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        var count = 0
        val result = regex.replace(source) {
            count++
            newValue
        }
        return ReplacementPreview(source, result, count)
    }

    fun tokenPairs(oldValue: String, newValue: String): List<Pair<String, String>> {
        val oldTokens = oldValue.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val newTokens = newValue.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val pairs = mutableListOf<Pair<String, String>>()
        if (oldValue.isNotBlank() && newValue.isNotBlank()) pairs += oldValue.trim() to newValue.trim()
        if (oldTokens.size == newTokens.size) {
            oldTokens.zip(newTokens).filter { (a, b) -> !a.equals(b, true) }.forEach { pairs += it }
        }
        return pairs.distinctBy { it.first.lowercase() }
    }
}
