package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.AddressType

/**
 * Pure-logic Bitcoin utility functions extracted from WalletRepository
 * for testability. These functions handle address validation, key format
 * detection, Base58 encoding, and key conversion — all critical paths
 * where a bug could cause fund loss.
 *
 * No Android dependencies. No BDK dependencies. Pure Kotlin + JDK crypto.
 */
object BitcoinUtils {

    // ── Address Type Detection ───────────────────────────────────────

    /**
     * Detect the address type from a Bitcoin address string.
     * Supports mainnet (1, 3, bc1q, bc1p) and testnet (m, n, 2, tb1q, tb1p).
     */
    fun detectAddressType(address: String): AddressType? {
        val trimmed = address.trim()
        return when {
            trimmed.startsWith("1") -> AddressType.LEGACY
            trimmed.startsWith("3") -> AddressType.NESTED_SEGWIT
            trimmed.startsWith("bc1q") || trimmed.startsWith("tb1q") -> AddressType.SEGWIT
            trimmed.startsWith("bc1p") || trimmed.startsWith("tb1p") -> AddressType.TAPROOT
            trimmed.startsWith("m") || trimmed.startsWith("n") -> AddressType.LEGACY // testnet P2PKH
            trimmed.startsWith("2") -> AddressType.NESTED_SEGWIT // testnet P2SH
            else -> null
        }
    }

    // ── Watch-Only Detection ─────────────────────────────────────────

    /**
     * Check if input string represents a watch-only key material.
     * Supports:
     * - Bare xpub/zpub/ypub/tpub/vpub/upub
     * - Origin-prefixed: "[fingerprint/path]xpub..."
     * - Full output descriptors: wpkh([fingerprint/path]xpub.../0/wildcard)
     */
    fun isWatchOnlyInput(input: String): Boolean {
        val trimmed = input.trim()

        // Bare xpub/zpub/ypub/tpub/vpub/upub
        if (trimmed.startsWith("xpub") || trimmed.startsWith("ypub") ||
            trimmed.startsWith("zpub") || trimmed.startsWith("tpub") ||
            trimmed.startsWith("vpub") || trimmed.startsWith("upub")
        ) {
            return true
        }

        // Origin-prefixed: [fingerprint/path]xpub
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            val afterBracket = trimmed.substringAfter("]")
            if (afterBracket.startsWith("xpub") || afterBracket.startsWith("ypub") ||
                afterBracket.startsWith("zpub") || afterBracket.startsWith("tpub") ||
                afterBracket.startsWith("vpub") || afterBracket.startsWith("upub")
            ) {
                return true
            }
        }

        // Full output descriptor with public key
        val descriptorPrefixes = listOf("pkh(", "wpkh(", "tr(", "sh(wpkh(", "sh(")
        if (descriptorPrefixes.any { trimmed.lowercase().startsWith(it) }) {
            val hasPublicKey =
                trimmed.contains("xpub") || trimmed.contains("tpub") ||
                    trimmed.contains("ypub") || trimmed.contains("zpub") ||
                    trimmed.contains("vpub") || trimmed.contains("upub")
            val hasPrivateKey = trimmed.contains("xprv") || trimmed.contains("tprv")
            return hasPublicKey && !hasPrivateKey
        }

        return false
    }

    // ── WIF Private Key Validation ───────────────────────────────────

    /**
     * Check if input string is a WIF (Wallet Import Format) private key.
     * Mainnet: starts with 'K' or 'L' (compressed, 52 chars) or '5' (uncompressed, 51 chars)
     * Testnet: starts with 'c' (compressed) or '9' (uncompressed)
     * Validates via Base58Check decode: version byte 0x80 (mainnet) or 0xEF (testnet).
     */
    fun isWifPrivateKey(input: String): Boolean {
        val trimmed = input.trim()
        val couldBeWif =
            (trimmed.length == 52 && (trimmed[0] == 'K' || trimmed[0] == 'L' || trimmed[0] == 'c')) ||
                (trimmed.length == 51 && (trimmed[0] == '5' || trimmed[0] == '9'))
        if (!couldBeWif) return false

        return try {
            val decoded = Base58.decodeChecked(trimmed)
            val version = decoded[0].toInt() and 0xFF
            val validVersion = version == 0x80 || version == 0xEF
            val validLength = decoded.size == 34 || decoded.size == 33
            validVersion && validLength
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if a WIF key is compressed (K/L/c prefix, 52 chars).
     */
    fun isWifCompressed(wif: String): Boolean {
        val trimmed = wif.trim()
        return trimmed.length == 52 && (trimmed[0] == 'K' || trimmed[0] == 'L' || trimmed[0] == 'c')
    }

    // ── Fingerprint Extraction ───────────────────────────────────────

    /**
     * Extract master fingerprint from key material string.
     * Parses "[fingerprint/...]xpub" and "wpkh([fingerprint/...]xpub/0/wildcard)" formats.
     * Returns null if no fingerprint found.
     */
    fun extractFingerprint(keyMaterial: String): String? {
        val pattern = """\[([a-fA-F0-9]{8})/""".toRegex()
        return pattern.find(keyMaterial.trim())?.groupValues?.get(1)?.lowercase()
    }

    // ── Key Origin Parsing ───────────────────────────────────────────

    /**
     * Parsed key origin info from an extended key string.
     */
    data class KeyOriginInfo(
        val bareKey: String,
        val fingerprint: String?,
        val derivationPath: String?,
    )

    /**
     * Parse key origin info from "[fingerprint/path]xpub..." format.
     * Returns the bare key and any origin info found.
     * Handles both ' and h notation for hardened derivation.
     */
    fun parseKeyOrigin(input: String): KeyOriginInfo {
        val originPattern = """\[([a-fA-F0-9]{8})/([^]]+)](.+)""".toRegex()
        val match = originPattern.find(input.trim())

        return if (match != null) {
            val fingerprint = match.groupValues[1].lowercase()
            val path =
                match.groupValues[2]
                    .replace("H", "'")
                    .replace("h", "'")
            val bareKey = match.groupValues[3]
            KeyOriginInfo(bareKey, fingerprint, path)
        } else {
            KeyOriginInfo(input.trim(), null, null)
        }
    }

    // ── Extended Key Conversion ──────────────────────────────────────

    /**
     * Convert zpub/ypub/vpub/upub to xpub/tpub via Base58Check re-encode.
     * xpub and tpub pass through unchanged.
     *
     * Version byte mappings:
     * - xpub: 0x0488B21E (mainnet)
     * - tpub: 0x043587CF (testnet)
     * - ypub: 0x049D7CB2 (mainnet BIP49)
     * - upub: 0x044A5262 (testnet BIP49)
     * - zpub: 0x04B24746 (mainnet BIP84)
     * - vpub: 0x045F1CF6 (testnet BIP84)
     */
    fun convertToXpub(extendedKey: String): String {
        if (extendedKey.startsWith("xpub") || extendedKey.startsWith("tpub")) {
            return extendedKey
        }

        val decoded = Base58.decodeChecked(extendedKey)

        val isTestnet = extendedKey.startsWith("vpub") || extendedKey.startsWith("upub")
        val targetVersion =
            if (isTestnet) {
                byteArrayOf(0x04, 0x35, 0x87.toByte(), 0xCF.toByte()) // tpub
            } else {
                byteArrayOf(0x04, 0x88.toByte(), 0xB2.toByte(), 0x1E) // xpub
            }

        val newData = targetVersion + decoded.sliceArray(4 until decoded.size)
        return Base58.encodeChecked(newData)
    }

    // ── Base58 Encoding/Decoding ─────────────────────────────────────

    /**
     * Base58 encoding/decoding with checksum support.
     * Used for Bitcoin address and extended key serialization.
     */
    object Base58 {
        private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val INDEXES =
            IntArray(128) { -1 }.also { arr ->
                ALPHABET.forEachIndexed { i, c -> arr[c.code] = i }
            }

        /**
         * Decode a Base58Check string, verifying the 4-byte checksum.
         * Returns the payload (without checksum).
         * @throws IllegalArgumentException if checksum is invalid
         */
        fun decodeChecked(input: String): ByteArray {
            val decoded = decode(input)
            val data = decoded.sliceArray(0 until decoded.size - 4)
            val checksum = decoded.sliceArray(decoded.size - 4 until decoded.size)
            val hash = sha256(sha256(data))
            val expectedChecksum = hash.sliceArray(0 until 4)
            require(checksum.contentEquals(expectedChecksum)) { "Invalid checksum" }
            return data
        }

        /**
         * Encode data with a 4-byte double-SHA256 checksum appended.
         */
        fun encodeChecked(data: ByteArray): String {
            val hash = sha256(sha256(data))
            val checksum = hash.sliceArray(0 until 4)
            return encode(data + checksum)
        }

        /**
         * Raw Base58 decode (no checksum verification).
         */
        fun decode(input: String): ByteArray {
            if (input.isEmpty()) return ByteArray(0)

            var bi = java.math.BigInteger.ZERO
            for (c in input) {
                val digit = INDEXES[c.code]
                require(digit >= 0) { "Invalid Base58 character: $c" }
                bi = bi.multiply(java.math.BigInteger.valueOf(58))
                    .add(java.math.BigInteger.valueOf(digit.toLong()))
            }

            val bytes = bi.toByteArray()
            val stripSign = bytes.isNotEmpty() && bytes[0] == 0.toByte() && bytes.size > 1
            val stripped = if (stripSign) bytes.sliceArray(1 until bytes.size) else bytes

            var leadingZeros = 0
            for (c in input) {
                if (c == '1') leadingZeros++ else break
            }

            return ByteArray(leadingZeros) + stripped
        }

        /**
         * Raw Base58 encode (no checksum).
         */
        fun encode(input: ByteArray): String {
            if (input.isEmpty()) return ""

            var leadingZeros = 0
            for (b in input) {
                if (b == 0.toByte()) leadingZeros++ else break
            }

            var bi = java.math.BigInteger(1, input)
            val sb = StringBuilder()

            while (bi > java.math.BigInteger.ZERO) {
                val (quotient, remainder) = bi.divideAndRemainder(java.math.BigInteger.valueOf(58))
                sb.append(ALPHABET[remainder.toInt()])
                bi = quotient
            }

            repeat(leadingZeros) { sb.append('1') }

            return sb.reverse().toString()
        }

        fun sha256(data: ByteArray): ByteArray {
            return java.security.MessageDigest.getInstance("SHA-256").digest(data)
        }
    }

    // ── Fee Rate Conversion ──────────────────────────────────────────

    /**
     * Convert a sat/vB fee rate (potentially fractional, e.g. 0.5) to sat/kWU.
     * 1 sat/vB = 250 sat/kWU, so 0.8 sat/vB = 200 sat/kWU.
     * Result is clamped to at least 1 (minimum relay fee).
     */
    fun feeRateToSatPerKwu(satPerVb: Double): ULong {
        return kotlin.math.round(satPerVb * 250.0)
            .toLong()
            .coerceAtLeast(1L)
            .toULong()
    }

    /**
     * Compute exact fee in sats from a target rate (sat/vB) and measured vsize.
     * Uses round() so the effective rate (fee/vsize) is centered on the target
     * rather than always above it. Max error is +/-0.5/vsize sat/vB.
     * Returns null if vsize <= 0 or resulting fee <= 0.
     */
    fun computeExactFeeSats(targetSatPerVb: Double, vsize: Double): ULong? {
        if (vsize <= 0.0) return null
        val exactFee = kotlin.math.round(targetSatPerVb * vsize).toLong()
        return if (exactFee > 0) exactFee.toULong() else null
    }

    // ── Script Hash ──────────────────────────────────────────────────

    /**
     * Compute an Electrum-style script hash from raw scriptPubKey bytes.
     * Electrum uses reversed SHA-256 of the scriptPubKey as the "script hash".
     */
    fun computeScriptHash(scriptBytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(scriptBytes)
        return hash.reversedArray().joinToString("") { "%02x".format(it) }
    }

    // ── Descriptor String Building ───────────────────────────────────

    /**
     * Build a descriptor key expression with origin info.
     * Produces: [fingerprint/path]xpub
     * Uses "00000000" as fallback fingerprint when none is provided
     * (triggers SeedSigner's missing-fingerprint fallback).
     */
    fun buildKeyWithOrigin(
        xpubKey: String,
        fingerprint: String?,
        derivationPath: String?,
        addressType: AddressType,
    ): String {
        val effectiveFingerprint = fingerprint ?: "00000000"
        val path = derivationPath ?: addressType.accountPath
        return "[$effectiveFingerprint/$path]$xpubKey"
    }

    /**
     * Wrap a key expression in the appropriate descriptor function for the given address type.
     * Returns a pair of (external descriptor string, internal descriptor string).
     */
    fun buildDescriptorStrings(
        keyWithOrigin: String,
        addressType: AddressType,
    ): Pair<String, String> {
        return when (addressType) {
            AddressType.LEGACY -> Pair(
                "pkh($keyWithOrigin/0/*)",
                "pkh($keyWithOrigin/1/*)",
            )
            AddressType.NESTED_SEGWIT -> Pair(
                "sh(wpkh($keyWithOrigin/0/*))",
                "sh(wpkh($keyWithOrigin/1/*))",
            )
            AddressType.SEGWIT -> Pair(
                "wpkh($keyWithOrigin/0/*)",
                "wpkh($keyWithOrigin/1/*)",
            )
            AddressType.TAPROOT -> Pair(
                "tr($keyWithOrigin/0/*)",
                "tr($keyWithOrigin/1/*)",
            )
        }
    }

    // ── Descriptor Parsing ───────────────────────────────────────────

    /**
     * Strip checksum suffix from a descriptor string.
     * e.g. "wpkh(...)#abcdef12" -> "wpkh(...)"
     */
    fun stripDescriptorChecksum(descriptor: String): String {
        val trimmed = descriptor.trim()
        val checksumSuffix = trimmed.substringAfterLast('#', "")
        return trimmed.removeSuffix("#$checksumSuffix").trim()
    }

    /**
     * Check if a descriptor string is a full output descriptor
     * (starts with pkh(, wpkh(, tr(, sh(wpkh(, or sh().
     */
    fun isFullDescriptor(input: String): Boolean {
        val descriptorPrefixes = listOf("pkh(", "wpkh(", "tr(", "sh(wpkh(", "sh(")
        return descriptorPrefixes.any { input.trim().lowercase().startsWith(it) }
    }

    /**
     * Check if a descriptor contains BIP 389 multipath syntax (<0;1> or <1;0>).
     */
    fun isBip389Multipath(descriptor: String): Boolean {
        return descriptor.contains("<0;1>") || descriptor.contains("<1;0>")
    }

    /**
     * Determine if a BIP 389 multipath descriptor has reversed ordering (<1;0>).
     */
    fun isBip389Reversed(descriptor: String): Boolean {
        return descriptor.contains("<1;0>")
    }

    /**
     * Derive external/internal descriptor strings from a single full descriptor.
     * Handles:
     * - Descriptors with /0/wildcard) -> external; /1/wildcard) for internal
     * - Descriptors with /1/wildcard) -> internal; /0/wildcard) for external
     * - Descriptors with no child path -> appends /0/wildcard and /1/wildcard
     *
     * Returns pair of (external string, internal string).
     */
    fun deriveDescriptorPair(descriptor: String): Pair<String, String> {
        val trimmed = stripDescriptorChecksum(descriptor)

        return if (trimmed.contains("/0/*)")) {
            Pair(trimmed, trimmed.replace("/0/*)", "/1/*)"))
        } else if (trimmed.contains("/1/*)")) {
            Pair(trimmed.replace("/1/*)", "/0/*)"), trimmed)
        } else {
            // No child path specified - add /0/* and /1/*
            val base = trimmed.trimEnd(')')
            val closingParens = ")".repeat(trimmed.length - trimmed.trimEnd(')').length)
            Pair("$base/0/*$closingParens", "$base/1/*$closingParens")
        }
    }

    // ── Weight Estimation (Fallback) ─────────────────────────────────

    /**
     * Input weight in weight units for each address type.
     * These are reference values from bitcoinops.org/en/tools/calc-size/
     */
    fun inputWeightWU(addressType: AddressType): Long {
        return when (addressType) {
            AddressType.LEGACY -> 592L
            AddressType.NESTED_SEGWIT -> 364L
            AddressType.SEGWIT -> 272L
            AddressType.TAPROOT -> 230L
        }
    }

    /**
     * Estimate signed transaction vsize from known components when
     * actual signing is not possible (watch-only wallets).
     *
     * @param numInputs number of transaction inputs
     * @param inputWeightWU weight units per input (from inputWeightWU() or BDK maxWeightToSatisfy)
     * @param outputScriptLengths list of scriptPubKey byte lengths for each output
     * @return estimated vsize (ceil of weight/4)
     */
    fun estimateVsizeFromComponents(
        numInputs: Int,
        inputWeightWU: Long,
        outputScriptLengths: List<Int>,
    ): Double {
        val isSegwit = inputWeightWU < 500L
        val overheadWU = if (isSegwit) 42L else 40L

        var totalOutputWU = 0L
        for (scriptLen in outputScriptLengths) {
            totalOutputWU += (9L + scriptLen) * 4L
        }

        val totalWU = overheadWU + (numInputs.toLong() * inputWeightWU) + totalOutputWU
        return kotlin.math.ceil(totalWU.toDouble() / 4.0)
    }

    // ── Backup JSON Parsing ──────────────────────────────────────────

    /**
     * Parsed wallet backup data — pure data extracted from JSON,
     * before any BDK/Android operations.
     */
    data class BackupWalletData(
        val name: String,
        val addressType: AddressType,
        val network: String, // "BITCOIN" or "TESTNET"
        val seedFormat: String, // "BIP39" etc.
        val keyMaterial: String,
        val isWatchOnly: Boolean,
        val customDerivationPath: String?,
    )

    /**
     * Parse a backup JSON object into a BackupWalletData.
     * Extracts and validates fields with sensible defaults.
     * Throws if required fields (wallet, keyMaterial with mnemonic or xpub) are missing.
     *
     * @param walletJson the "wallet" sub-object
     * @param keyMaterialJson the "keyMaterial" sub-object
     */
    fun parseBackupJson(
        walletJson: org.json.JSONObject,
        keyMaterialJson: org.json.JSONObject,
    ): BackupWalletData {
        val addressType = try {
            AddressType.valueOf(walletJson.getString("addressType"))
        } catch (_: Exception) {
            AddressType.SEGWIT
        }

        val network = try {
            val n = walletJson.optString("network", "BITCOIN")
            // Validate it's a known value
            if (n == "BITCOIN" || n == "TESTNET" || n == "SIGNET" || n == "REGTEST") n else "BITCOIN"
        } catch (_: Exception) {
            "BITCOIN"
        }

        val seedFormat = try {
            val sf = walletJson.optString("seedFormat", "BIP39")
            if (sf.isNotBlank()) sf else "BIP39"
        } catch (_: Exception) {
            "BIP39"
        }

        val mnemonic = keyMaterialJson.optString("mnemonic", null.toString()).let {
            if (it == "null" || it.isBlank()) null else it
        }
        val xpub = keyMaterialJson.optString("extendedPublicKey", null.toString()).let {
            if (it == "null" || it.isBlank()) null else it
        }

        val keyMaterial = mnemonic ?: xpub
            ?: throw IllegalStateException("No key material found in backup")

        val isWatchOnly = mnemonic == null

        val customDerivationPath = walletJson.optString("derivationPath", null.toString()).let {
            if (it == "null" || it.isBlank()) null else it
        }

        return BackupWalletData(
            name = walletJson.optString("name", "Restored Wallet"),
            addressType = addressType,
            network = network,
            seedFormat = seedFormat,
            keyMaterial = keyMaterial,
            isWatchOnly = isWatchOnly,
            customDerivationPath = customDerivationPath,
        )
    }

    // ── Fee Estimation JSON Parsing ──────────────────────────────────

    /**
     * Parsed fee estimates — pure data from mempool.space JSON.
     */
    data class ParsedFeeEstimates(
        val fastestFee: Double,
        val halfHourFee: Double,
        val hourFee: Double,
        val minimumFee: Double,
    )

    /**
     * Parse a mempool.space fee estimation JSON string into fee estimates.
     * Both /api/v1/fees/precise and /api/v1/fees/recommended use the same field names.
     * Defaults to 1.0 sat/vB for any missing field.
     */
    fun parseFeeEstimatesJson(jsonString: String): ParsedFeeEstimates {
        val json = org.json.JSONObject(jsonString)
        return ParsedFeeEstimates(
            fastestFee = json.optDouble("fastestFee", 1.0),
            halfHourFee = json.optDouble("halfHourFee", 1.0),
            hourFee = json.optDouble("hourFee", 1.0),
            minimumFee = json.optDouble("minimumFee", 1.0),
        )
    }
}
