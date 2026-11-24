package dz.meals.app.data

/**
 * Folds user search text without changing stored names or stable food IDs.
 * Supports Arabic vocalization/hamza variants, common Latin diacritics and
 * whitespace/punctuation differences. It intentionally does not translate.
 */
fun normalizeFoodSearchText(value: String): String {
    val folded = buildString(value.length) {
        for (character in value.lowercase()) {
            when (character) {
                in '\u064B'..'\u065F', '\u0670', '\u0640', '\u200C', '\u200D' -> Unit
                'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                'ؤ' -> append('و')
                'ئ', 'ى', 'ی' -> append('ي')
                'ک' -> append('ك')
                'à', 'á', 'â', 'ä', 'ã', 'å' -> append('a')
                'æ' -> append("ae")
                'ç' -> append('c')
                'è', 'é', 'ê', 'ë' -> append('e')
                'ì', 'í', 'î', 'ï' -> append('i')
                'ñ' -> append('n')
                'ò', 'ó', 'ô', 'ö', 'õ' -> append('o')
                'œ' -> append("oe")
                'ù', 'ú', 'û', 'ü' -> append('u')
                'ý', 'ÿ' -> append('y')
                else -> append(character)
            }
        }
    }
    return folded
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

/**
 * Matches all query terms against a combined food search index. Terms can be
 * found in different aliases/locale fields, so a query can mix Arabic and
 * Latin transliteration without requiring one exact stored phrase.
 */
fun foodMatchesSearch(fields: Iterable<String>, query: String): Boolean {
    val normalizedQuery = normalizeFoodSearchText(query)
    if (normalizedQuery.isBlank()) return false
    val terms = normalizedQuery.split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return false
    val normalizedIndex = normalizeFoodSearchText(fields.joinToString(" "))
    return terms.all { term -> term in normalizedIndex }
}
