package com.bell.launcher.util

/**
 * Перша літера для алфавітного покажчика.
 *
 * Кирилиця зводиться до латиниці, щоб «Диск» і «Dolby Atmos» опинилися
 * в одній секції «D» — так само працює покажчик у Niagara.
 */
object IndexLetters {

    const val FAVORITES = "★"
    const val OTHER = "#"

    private val cyrillic: Map<Char, Char> = mapOf(
        'А' to 'A', 'Б' to 'B', 'В' to 'V', 'Г' to 'G', 'Ґ' to 'G', 'Д' to 'D',
        'Е' to 'E', 'Є' to 'E', 'Ё' to 'E', 'Ж' to 'Z', 'З' to 'Z', 'И' to 'I',
        'І' to 'I', 'Ї' to 'I', 'Й' to 'I', 'К' to 'K', 'Л' to 'L', 'М' to 'M',
        'Н' to 'N', 'О' to 'O', 'П' to 'P', 'Р' to 'R', 'С' to 'S', 'Т' to 'T',
        'У' to 'U', 'Ф' to 'F', 'Х' to 'H', 'Ц' to 'C', 'Ч' to 'C', 'Ш' to 'S',
        'Щ' to 'S', 'Ъ' to 'Y', 'Ы' to 'Y', 'Ь' to 'Y', 'Э' to 'E', 'Ю' to 'U',
        'Я' to 'Y',
    )

    fun of(label: String): String {
        val first = label.trim().firstOrNull() ?: return OTHER
        val upper = first.uppercaseChar()
        cyrillic[upper]?.let { return it.toString() }
        if (upper in 'A'..'Z') return upper.toString()
        return OTHER
    }

    /** Порядок у покажчику: ★, #, далі A…Z. */
    fun sortLetters(letters: Collection<String>): List<String> {
        val rest = letters.filter { it != FAVORITES && it != OTHER }.sorted()
        return buildList {
            if (FAVORITES in letters) add(FAVORITES)
            if (OTHER in letters) add(OTHER)
            addAll(rest)
        }
    }
}
