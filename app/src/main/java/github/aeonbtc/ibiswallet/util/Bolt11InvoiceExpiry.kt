package github.aeonbtc.ibiswallet.util

import lwk.Bolt11Invoice

/**
 * Absolute epoch ms when a BOLT11 invoice expires, from server/protocol fields
 * (`timestamp` + `expiryTime`). Null if the invoice cannot be parsed.
 */
fun bolt11InvoiceExpiresAtMs(invoice: String): Long? {
    if (invoice.isBlank()) return null
    return runCatching {
        val bolt11 = Bolt11Invoice(invoice)
        try {
            val timestampSec = bolt11.timestamp().toLong()
            val expirySec = bolt11.expiryTime().toLong()
            if (timestampSec <= 0L || expirySec < 0L) {
                null
            } else {
                (timestampSec + expirySec) * 1000L
            }
        } finally {
            runCatching { bolt11.destroy() }
        }
    }.getOrNull()
}
