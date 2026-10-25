package de.handeltheke.handeldesk.data.repo

import androidx.sqlite.db.SimpleSQLiteQuery
import de.handeltheke.handeldesk.core.SearchClause
import de.handeltheke.handeldesk.core.SearchSyntax
import de.handeltheke.handeldesk.core.normalizeSearch
import de.handeltheke.handeldesk.data.local.ProductDao
import de.handeltheke.handeldesk.data.local.ProductEntity

data class ProductPage(val items: List<ProductEntity>, val total: Long, val page: Int, val pageSize: Int, val errors: List<String>)

class ProductSearchRepository(private val dao: ProductDao) {
    suspend fun search(query: String, page: Int = 1, pageSize: Int = 20, state: String? = null): ProductPage {
        val parsed = SearchSyntax.parse(query)
        val where = mutableListOf("1=1")
        val args = mutableListOf<Any>()
        if (!state.isNullOrBlank()) { where += "state=?"; args += state }
        parsed.positiveGroups.forEach { group ->
            val sub = mutableListOf<String>()
            group.forEach { clause -> val (sql, values) = clauseSql(clause); sub += sql; args += values }
            if (sub.isNotEmpty()) where += "(" + sub.joinToString(" OR ") + ")"
        }
        parsed.excluded.forEach { clause ->
            val (sql, values) = clauseSql(clause); where += "NOT ($sql)"; args += values
        }
        val whereSql = where.joinToString(" AND ")
        val total = dao.rawCount(SimpleSQLiteQuery("SELECT COUNT(*) FROM products WHERE $whereSql", args.toTypedArray()))
        val safePageSize = pageSize.coerceIn(1, 100)
        val pages = if (total == 0L) 1 else ((total + safePageSize - 1) / safePageSize).toInt()
        val safePage = page.coerceIn(1, pages)
        val listArgs = args.toMutableList().apply { add(safePageSize); add((safePage - 1) * safePageSize) }
        val items = dao.rawList(SimpleSQLiteQuery(
            "SELECT * FROM products WHERE $whereSql ORDER BY CASE WHEN productId IS NULL THEN 0 ELSE 1 END, COALESCE(productId,0) DESC LIMIT ? OFFSET ?",
            listArgs.toTypedArray()
        ))
        return ProductPage(items, total, safePage, safePageSize, parsed.errors)
    }

    private fun clauseSql(c: SearchClause): Pair<String, List<Any>> {
        val raw = "%${c.value}%"
        val normalized = "%${normalizeSearch(c.value)}%"
        return when (c.field) {
            "id" -> "CAST(productId AS TEXT) LIKE ?" to listOf(raw)
            "name", "model", "sku", "jan" -> "${c.field} LIKE ?" to listOf(raw)
            "ean", "upc", "mpn", "isbn" -> if (c.phrase) "localJson LIKE ?" to listOf(raw) else "(localJson LIKE ? OR searchNorm LIKE ?)" to listOf(raw, normalized)
            else -> if (c.phrase) {
                "(name LIKE ? OR model LIKE ? OR sku LIKE ? OR jan LIKE ? OR localJson LIKE ?)" to List(5) { raw }
            } else {
                "(name LIKE ? OR model LIKE ? OR sku LIKE ? OR jan LIKE ? OR CAST(productId AS TEXT) LIKE ? OR searchNorm LIKE ?)" to listOf(raw, raw, raw, raw, raw, normalized)
            }
        }
    }
}
