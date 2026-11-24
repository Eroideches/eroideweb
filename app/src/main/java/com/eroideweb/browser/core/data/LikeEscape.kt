package com.eroideweb.browser.core.data

/**
 * Prepara il testo digitato per una clausola `LIKE ... ESCAPE '\'`: `%` e `_`
 * scritti dall'utente devono valere come caratteri, non come jolly.
 */
fun escapeLike(query: String): String = buildString(query.length) {
    query.forEach { char ->
        if (char == '\\' || char == '%' || char == '_') append('\\')
        append(char)
    }
}
