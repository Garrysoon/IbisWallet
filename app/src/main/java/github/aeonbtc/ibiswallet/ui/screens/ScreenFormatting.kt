package github.aeonbtc.ibiswallet.ui.screens

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SATS_PER_BTC = 100_000_000.0

fun formatBtc(sats: ULong): String {
    val btc = sats.toDouble() / SATS_PER_BTC
    return String.format(Locale.US, "%.8f", btc)
}

fun formatSats(sats: ULong): String = NumberFormat.getNumberInstance(Locale.US).format(sats.toLong())

fun formatAmount(
    sats: ULong,
    useSats: Boolean,
    includeUnit: Boolean = false,
): String {
    val amount = if (useSats) formatSats(sats) else formatBtc(sats)
    if (!includeUnit) return amount
    return if (useSats) "$amount sats" else "$amount BTC"
}

fun formatUsd(amount: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(amount)

fun formatFullTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatVBytes(vBytes: Double): String {
    val formatted = String.format(Locale.US, "%.2f", vBytes)
    return formatted.trimEnd('0').trimEnd('.')
}
