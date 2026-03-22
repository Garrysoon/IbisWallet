package github.aeonbtc.ibiswallet.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val transactionDateSearchPatterns =
    listOf(
        "MMM d, yyyy · HH:mm",
        "MMM d yyyy HH:mm",
        "MMM d, yyyy",
        "MMM d yyyy",
        "MMM d",
        "MMM yyyy",
        "MMMM d, yyyy · HH:mm",
        "MMMM d yyyy HH:mm",
        "MMMM d, yyyy",
        "MMMM d yyyy",
        "MMMM d",
        "MMMM yyyy",
        "d MMM yyyy HH:mm",
        "d MMM yyyy",
        "d MMM",
        "d MMMM yyyy HH:mm",
        "d MMMM yyyy",
        "d MMMM",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm",
        "yyyy/MM/dd",
        "M/d/yyyy HH:mm",
        "M/d/yyyy",
        "M/d HH:mm",
        "M/d",
        "MM/dd/yyyy HH:mm",
        "MM/dd/yyyy",
        "MM/dd HH:mm",
        "MM/dd",
        "d/M/yyyy HH:mm",
        "d/M/yyyy",
        "d/M HH:mm",
        "d/M",
        "dd/MM/yyyy HH:mm",
        "dd/MM/yyyy",
        "dd/MM HH:mm",
        "dd/MM",
        "HH:mm",
        "yyyy",
    )

fun matchesTimestampSearch(
    timestampSeconds: Long?,
    query: String,
): Boolean {
    if (timestampSeconds == null) return false

    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return false

    val rawQuery = trimmedQuery.lowercase()
    val normalizedQuery = normalizeDateSearchValue(trimmedQuery)
    val compactQuery = normalizedQuery.replace(" ", "")
    val date = Date(timestampSeconds * 1000)

    return transactionDateSearchLocales().any { locale ->
        transactionDateSearchPatterns.any { pattern ->
            val term = SimpleDateFormat(pattern, locale).format(date)
            val rawTerm = term.lowercase(locale)
            if (rawTerm.contains(rawQuery)) {
                return@any true
            }

            val normalizedTerm = normalizeDateSearchValue(term)
            if (normalizedQuery.isNotBlank() && normalizedTerm.contains(normalizedQuery)) {
                return@any true
            }

            val compactTerm = normalizedTerm.replace(" ", "")
            compactQuery.isNotBlank() && compactTerm.contains(compactQuery)
        }
    }
}

private fun transactionDateSearchLocales(): Set<Locale> =
    linkedSetOf(Locale.getDefault(), Locale.US)

private fun normalizeDateSearchValue(value: String): String =
    value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
