package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.AddressType
import org.json.JSONObject

/**
 * BIP 329 Wallet Labels Export Format.
 * See https://github.com/bitcoin/bips/blob/master/bip-0329.mediawiki
 *
 * Supports:
 * - Export/Import of JSONL (newline-delimited JSON) label files
 * - Import of Electrum CSV history files (txid,label,...)
 *
 * Record types: tx, addr, pubkey, input, output, xpub
 * Ibis stores tx and addr labels; other types are parsed but only tx/addr are persisted.
 */
object Bip329Labels {

    /** BIP 329 record types */
    enum class Type {
        tx,
        addr,
        pubkey,
        input,
        output,
        xpub,
    }

    /**
     * Build the BIP 329 `origin` field for a wallet.
     * Returns an abbreviated output descriptor with key origin but no actual keys.
     * Example: "wpkh([d34db33f/84'/0'/0'])"
     */
    fun buildOrigin(
        addressType: AddressType,
        fingerprint: String?,
    ): String? {
        if (fingerprint.isNullOrBlank() || fingerprint.length != 8) return null
        val fp = fingerprint.lowercase()
        val path = addressType.accountPath
        return when (addressType) {
            AddressType.LEGACY -> "pkh([$fp/$path])"
            AddressType.NESTED_SEGWIT -> "sh(wpkh([$fp/$path]))"
            AddressType.SEGWIT -> "wpkh([$fp/$path])"
            AddressType.TAPROOT -> "tr([$fp/$path])"
        }
    }

    /**
     * Export address and transaction labels to BIP 329 JSONL format.
     * Each line is a valid JSON object with type, ref, label, and optional origin.
     */
    fun export(
        addressLabels: Map<String, String>,
        transactionLabels: Map<String, String>,
        origin: String? = null,
    ): String {
        val lines = mutableListOf<String>()

        // Transaction labels first (following Sparrow convention)
        for ((txid, label) in transactionLabels) {
            if (label.isBlank()) continue
            val json = JSONObject().apply {
                put("type", Type.tx.name)
                put("ref", txid)
                put("label", label)
                if (origin != null) put("origin", origin)
            }
            lines.add(json.toString())
        }

        // Address labels
        for ((address, label) in addressLabels) {
            if (label.isBlank()) continue
            val json = JSONObject().apply {
                put("type", Type.addr.name)
                put("ref", address)
                put("label", label)
                if (origin != null) put("origin", origin)
            }
            lines.add(json.toString())
        }

        return lines.joinToString("\n")
    }

    /**
     * Parse BIP 329 JSONL content or Electrum CSV and return imported labels.
     * Handles mixed content gracefully — invalid lines are counted but skipped.
     *
     * Merge semantics: imported labels overwrite existing labels for matching refs,
     * but do not delete labels not present in the import.
     */
    fun import(content: String): ImportResult {
        val addressLabels = mutableMapOf<String, String>()
        val transactionLabels = mutableMapOf<String, String>()
        val outputSpendable = mutableMapOf<String, Boolean>()
        var totalLines = 0
        var errorLines = 0

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            totalLines++

            // Try BIP 329 JSON first
            val parsed = parseBip329Line(trimmed)
            if (parsed != null) {
                when (parsed.type) {
                    Type.tx -> {
                        if (!parsed.label.isNullOrEmpty()) {
                            transactionLabels[parsed.ref] = parsed.label
                        }
                    }
                    Type.addr -> {
                        if (!parsed.label.isNullOrEmpty()) {
                            addressLabels[parsed.ref] = parsed.label
                        }
                    }
                    Type.output -> {
                        // Handle spendable flag (frozen UTXOs)
                        if (parsed.spendable != null) {
                            outputSpendable[parsed.ref] = parsed.spendable
                        }
                    }
                    // pubkey, input, xpub — not stored in Ibis
                    else -> { }
                }
                continue
            }

            // Try Electrum CSV format: txid,label,...
            val csvResult = parseElectrumCsvLine(trimmed)
            if (csvResult != null) {
                transactionLabels[csvResult.first] = csvResult.second
                continue
            }

            errorLines++
        }

        return ImportResult(
            addressLabels = addressLabels,
            transactionLabels = transactionLabels,
            outputSpendable = outputSpendable,
            totalLines = totalLines,
            errorLines = errorLines,
        )
    }

    // -- Internal parsing helpers --

    private data class ParsedLabel(
        val type: Type,
        val ref: String,
        val label: String?,
        val origin: String?,
        val spendable: Boolean?,
    )

    private fun parseBip329Line(line: String): ParsedLabel? {
        return try {
            val json = JSONObject(line)
            val typeStr = json.optString("type", "")
            if (typeStr.isEmpty()) return null
            val type = try { Type.valueOf(typeStr) } catch (_: Exception) { return null }
            val ref = json.optString("ref", "")
            if (ref.isEmpty()) return null

            ParsedLabel(
                type = type,
                ref = ref,
                label = json.optString("label")?.takeIf { it.isNotEmpty() && it != "null" },
                origin = json.optString("origin")?.takeIf { it.isNotEmpty() && it != "null" },
                spendable = if (json.has("spendable")) json.optBoolean("spendable") else null,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse an Electrum CSV line. Matches any CSV row where one column is a 64-char
     * hex string (txid) and the next column is a non-empty label.
     * Skips header rows (where the "label" column literally says "label").
     */
    private fun parseElectrumCsvLine(line: String): Pair<String, String>? {
        val parts = parseCsvLine(line)
        if (parts.size < 2) return null

        // Find the column that looks like a txid (64-char hex)
        val txidIdx = parts.indexOfFirst { field ->
            field.length == 64 && field.all { c ->
                c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            }
        }
        if (txidIdx < 0) return null

        val labelIdx = txidIdx + 1
        if (labelIdx >= parts.size) return null

        val label = parts[labelIdx].trim()
        // Skip header row and empty labels
        if (label.isEmpty() || label.equals("label", ignoreCase = true)) return null

        return Pair(parts[txidIdx], label)
    }

    /**
     * Simple CSV line parser that handles quoted fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim().removeSurrounding("\""))
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim().removeSurrounding("\""))

        return result
    }

    /**
     * CBOR-encode a byte array as a CBOR byte string (major type 2).
     * Used to create ur:bytes URs for animated QR export.
     */
    fun cborEncodeByteString(data: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream(data.size + 5)
        val len = data.size
        when {
            len < 24 -> output.write(0x40 or len)
            len < 256 -> {
                output.write(0x58)
                output.write(len)
            }
            len < 65536 -> {
                output.write(0x59)
                output.write(len shr 8)
                output.write(len and 0xFF)
            }
            else -> {
                output.write(0x5a)
                output.write(len shr 24 and 0xFF)
                output.write(len shr 16 and 0xFF)
                output.write(len shr 8 and 0xFF)
                output.write(len and 0xFF)
            }
        }
        output.write(data)
        return output.toByteArray()
    }

    /**
     * Result of a BIP 329 / Electrum CSV import operation.
     */
    data class ImportResult(
        val addressLabels: Map<String, String>,
        val transactionLabels: Map<String, String>,
        val outputSpendable: Map<String, Boolean>,
        val totalLines: Int,
        val errorLines: Int,
    ) {
        val totalLabelsImported: Int
            get() = addressLabels.size + transactionLabels.size
        val isEmpty: Boolean
            get() = totalLabelsImported == 0
    }
}
