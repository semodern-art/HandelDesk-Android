package de.handeltheke.handeldesk.core

data class SearchClause(
    val field: String? = null,
    val value: String,
    val phrase: Boolean = false,
    val negated: Boolean = false,
)

data class SearchQuery(
    val positiveGroups: List<List<SearchClause>>,
    val excluded: List<SearchClause>,
    val errors: List<String> = emptyList(),
)

object SearchSyntax {
    private val fields = setOf("id", "name", "model", "sku", "jan", "ean", "upc", "mpn", "isbn")

    fun parse(input: String): SearchQuery {
        val text = input.trim()
        if (text.isEmpty()) return SearchQuery(emptyList(), emptyList())
        val tokenResult = tokenize(text)
        if (tokenResult.error != null) return SearchQuery(emptyList(), emptyList(), listOf(tokenResult.error))

        val groups = mutableListOf<MutableList<SearchClause>>()
        val excluded = mutableListOf<SearchClause>()
        val errors = mutableListOf<String>()
        var pendingOr = false

        tokenResult.tokens.forEach { token ->
            if (token.value.equals("OR", ignoreCase = true) && !token.quoted) {
                if (groups.isEmpty() || pendingOr) errors += "OR должен находиться между двумя условиями"
                pendingOr = true
                return@forEach
            }
            val clause = parseClause(token)
            if (clause.value.isBlank()) return@forEach
            if (clause.negated) {
                excluded += clause
                pendingOr = false
            } else if (pendingOr && groups.isNotEmpty()) {
                groups.last() += clause
                pendingOr = false
            } else {
                groups += mutableListOf(clause)
                pendingOr = false
            }
        }
        if (pendingOr) errors += "После OR отсутствует условие"
        return SearchQuery(groups, excluded, errors)
    }

    private fun parseClause(token: Token): SearchClause {
        var raw = token.value
        val negated = raw.startsWith("-") && raw.length > 1
        if (negated) raw = raw.drop(1)
        var field: String? = null
        var value = raw
        val colon = raw.indexOf(':')
        if (colon > 0) {
            val maybe = raw.substring(0, colon).lowercase()
            val rest = raw.substring(colon + 1)
            if (maybe in fields && rest.isNotEmpty()) {
                field = maybe
                value = rest
            }
        }
        return SearchClause(field, value, token.quoted || value.any(Char::isWhitespace), negated)
    }

    private data class Token(val value: String, val quoted: Boolean)
    private data class TokenResult(val tokens: List<Token>, val error: String? = null)

    private fun tokenize(text: String): TokenResult {
        val out = mutableListOf<Token>()
        val current = StringBuilder()
        var quote = false
        var quotedToken = false
        var escaping = false

        fun flush() {
            if (current.isNotEmpty()) {
                out += Token(current.toString(), quotedToken)
                current.clear()
                quotedToken = false
            }
        }

        for (ch in text) {
            if (escaping) {
                current.append(ch)
                escaping = false
                continue
            }
            when {
                ch == '\\' -> escaping = true
                ch == '"' -> {
                    quote = !quote
                    quotedToken = true
                }
                ch.isWhitespace() && !quote -> flush()
                else -> current.append(ch)
            }
        }
        if (escaping) current.append('\\')
        if (quote) return TokenResult(out, "Незакрытая кавычка")
        flush()
        return TokenResult(out)
    }
}

fun normalizeSearch(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
