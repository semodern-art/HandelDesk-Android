package de.handeltheke.handeldesk.core

import java.text.Normalizer

object HandelthekeSeoPolicy {
    const val id = "handeltheke-product-v1"

    private val cyrillic = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "j", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "shh", 'ъ' to "",
        'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    fun slug(input: String): String {
        val lower = input.lowercase()
        val transliterated = buildString {
            lower.forEach { ch ->
                when {
                    ch in cyrillic -> append(cyrillic.getValue(ch))
                    ch == 'ß' -> append("ss")
                    else -> append(ch)
                }
            }
        }
        val decomposed = Normalizer.normalize(transliterated, Normalizer.Form.NFD)
        val asciiLike = buildString {
            decomposed.forEach { ch ->
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) append(ch)
            }
        }
        return asciiLike
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }
}
