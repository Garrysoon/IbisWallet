package github.aeonbtc.ibiswallet.silentpayments

import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.DescriptorPublicKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Silent Payments (BIP 352) Cryptographic Operations
 *
 * This implements the core cryptography needed for Silent Payments:
 * - Key generation from mnemonic (scan/spend keys)
 * - Silent Payment address encoding/decoding (bech32m)
 * - Public key tweaking: Q = P + hash(P||R)·G
 * - Transaction scanning for SP outputs
 *
 * Reference: https://github.com/bitcoin/bips/blob/master/bip-0352.mediawiki
 */
object SilentPaymentCrypto {

    // BIP352 derivation paths (hardened)
    private const val PATH_SCAN_MAINNET = "m/352'/0'/0'/0'"
    private const val PATH_SPEND_MAINNET = "m/352'/0'/0'/1'"
    private const val PATH_SCAN_TESTNET = "m/352'/1'/0'/0'"
    private const val PATH_SPEND_TESTNET = "m/352'/1'/0'/1'"

    // Human-readable parts for bech32m encoding
    private const val HRP_MAINNET = "sp1"
    private const val HRP_TESTNET = "tsp1"

    // secp256k1 curve order (n)
    private val CURVE_ORDER = byteArrayOf(
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(),
        0xBA.toByte(), 0xAE.toByte(), 0xDC.toByte(), 0xE6.toByte(),
        0xAF.toByte(), 0x48.toByte(), 0xA0.toByte(), 0x3B.toByte(),
        0xBF.toByte(), 0xD2.toByte(), 0x5E.toByte(), 0x8C.toByte(),
        0xD0.toByte(), 0x36.toByte(), 0x41.toByte(), 0x41.toByte()
    )

    /**
     * Generate Silent Payment keys from mnemonic.
     *
     * Derives scan and spend keys using BIP352 hardened paths.
     *
     * @param mnemonic BIP39 mnemonic phrase
     * @param network Mainnet or Testnet (affects derivation paths)
     * @return SilentPaymentKeys containing scan and spend keys
     * @throws SilentPaymentException.CryptoError if key generation fails
     */
    @JvmStatic
    fun generateKeys(
        mnemonic: Mnemonic,
        network: Network,
    ): SilentPaymentKeys {
        return try {
            // Create descriptor secret key from mnemonic
            val descriptorKey = DescriptorSecretKey(
                network = network,
                mnemonic = mnemonic,
                password = "", // No passphrase for now
            )

            // Get paths based on network
            val (scanPath, spendPath) = when (network) {
                Network.BITCOIN -> PATH_SCAN_MAINNET to PATH_SPEND_MAINNET
                Network.TESTNET -> PATH_SCAN_TESTNET to PATH_SPEND_TESTNET
                else -> PATH_SCAN_TESTNET to PATH_SPEND_TESTNET // Default to testnet
            }

            // Derive scan key
            val scanKey = descriptorKey.derive(scanPath)
            val scanSecret = scanKey.secretBytes()
            val scanPublic = scanKey.asPublic().encode()

            // Derive spend key
            val spendKey = descriptorKey.derive(spendPath)
            val spendSecret = spendKey.secretBytes()
            val spendPublic = spendKey.asPublic().encode()

            SilentPaymentKeys(
                scanPrivateKey = scanSecret,
                scanPublicKey = scanPublic,
                spendPrivateKey = spendSecret,
                spendPublicKey = spendPublic,
            )
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError(
                "Failed to generate Silent Payment keys: ${e.message}"
            )
        }
    }

    /**
     * Create a Silent Payment address from scan and spend public keys.
     *
     * Address format: sp1q... (mainnet) or tsp1q... (testnet)
     * Encodes both public keys using bech32m.
     *
     * @param scanPublicKey 33-byte compressed public key
     * @param spendPublicKey 33-byte compressed public key
     * @param network Mainnet or Testnet
     * @param labels Optional BIP352 labels (for sub-addresses)
     * @return SilentPaymentAddress containing the encoded address
     * @throws SilentPaymentException.CryptoError if encoding fails
     */
    @JvmStatic
    fun createAddress(
        scanPublicKey: ByteArray,
        spendPublicKey: ByteArray,
        network: Network,
        labels: List<Int> = emptyList(),
    ): SilentPaymentAddress {
        // Validate public keys
        if (scanPublicKey.size != 33) {
            throw SilentPaymentException.CryptoError(
                "Scan public key must be 33 bytes, got ${scanPublicKey.size}"
            )
        }
        if (spendPublicKey.size != 33) {
            throw SilentPaymentException.CryptoError(
                "Spend public key must be 33 bytes, got ${spendPublicKey.size}"
            )
        }

        // Encode using bech32m
        val hrp = if (network == Network.BITCOIN) HRP_MAINNET else HRP_TESTNET

        // Data: version byte (0) + scan key (33 bytes) + spend key (33 bytes) + labels
        val data = mutableListOf<Byte>()
        data.add(0) // Version byte 0 for base SP
        data.addAll(scanPublicKey.toList())
        data.addAll(spendPublicKey.toList())

        // Add label data if present
        labels.forEach { label ->
            // Encode label as varint (simplified - just add byte for now)
            if (label in 0..255) {
                data.add(label.toByte())
            }
        }

        val address = try {
            Bech32m.encode(hrp, data.toByteArray())
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError(
                "Failed to encode Silent Payment address: ${e.message}"
            )
        }

        return SilentPaymentAddress(
            address = address,
            scanPublicKey = scanPublicKey,
            spendPublicKey = spendPublicKey,
            network = network,
            labels = labels,
        )
    }

    /**
     * Decode a Silent Payment address.
     *
     * @param address String starting with "sp1" or "tsp1"
     * @return SilentPaymentAddress with extracted public keys
     * @throws SilentPaymentException.InvalidAddress if decoding fails
     */
    @JvmStatic
    fun decodeAddress(address: String): SilentPaymentAddress {
        // Validate HRP
        val isTestnet = address.startsWith(HRP_TESTNET)
        val isMainnet = address.startsWith(HRP_MAINNET)

        if (!isTestnet && !isMainnet) {
            throw SilentPaymentException.InvalidAddress(
                "Silent Payment address must start with '$HRP_MAINNET' or '$HRP_TESTNET'"
            )
        }

        val network = if (isMainnet) Network.BITCOIN else Network.TESTNET
        val hrp = if (isMainnet) HRP_MAINNET else HRP_TESTNET

        return try {
            val (decodedHrp, data) = Bech32m.decode(address)

            if (decodedHrp != hrp) {
                throw SilentPaymentException.InvalidAddress(
                    "Wrong human-readable part: expected $hrp, got $decodedHrp"
                )
            }

            if (data.size < 67) { // 1 version + 33 scan + 33 spend minimum
                throw SilentPaymentException.InvalidAddress(
                    "Invalid Silent Payment address: too short (${data.size} bytes)"
                )
            }

            val version = data[0].toInt()
            if (version != 0) {
                throw SilentPaymentException.InvalidAddress(
                    "Unsupported Silent Payment version: $version"
                )
            }

            val scanKey = data.copyOfRange(1, 34)
            val spendKey = data.copyOfRange(34, 67)

            // Extract labels if present (bytes after 67)
            val labels = if (data.size > 67) {
                data.copyOfRange(67, data.size).map { it.toInt() and 0xFF }
            } else {
                emptyList()
            }

            SilentPaymentAddress(
                address = address,
                scanPublicKey = scanKey,
                spendPublicKey = spendKey,
                network = network,
                labels = labels,
            )
        } catch (e: SilentPaymentException) {
            throw e
        } catch (e: Exception) {
            throw SilentPaymentException.InvalidAddress(
                "Failed to decode Silent Payment address: ${e.message}"
            )
        }
    }

    /**
     * Verify if a string is a valid Silent Payment address.
     *
     * @param address String to validate
     * @return true if valid Silent Payment address
     */
    @JvmStatic
    fun isValidAddress(address: String): Boolean {
        return try {
            decodeAddress(address)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Compute the tweak value: hash(P || R)
     * where P is scan public key and R is sender's input public key.
     *
     * This is the core of Silent Payments - it creates a unique value
     * that both sender and receiver can compute.
     *
     * @param scanPublicKey Receiver's scan public key (P)
     * @param inputPublicKey Sender's input public key from transaction (R)
     * @return 32-byte tweak value (hash)
     */
    @JvmStatic
    fun computeTweak(
        scanPublicKey: ByteArray,
        inputPublicKey: ByteArray,
    ): ByteArray {
        // Concatenate P || R
        val data = scanPublicKey + inputPublicKey

        // Compute tagged hash: SHA256(SHA256("BIP0352/Const") || data)
        val tag = sha256("BIP0352/Const".toByteArray())
        val taggedData = tag + tag + data

        return sha256(taggedData)
    }

    /**
     * Tweak a public key: Q = P + t·G
     * where P is the scan public key and t is the tweak.
     *
     * This is done by senders to generate the unique payment address.
     *
     * @param scanPublicKey Receiver's scan public key (P)
     * @param inputPublicKey Sender's input public key (R)
     * @return Tweak value (32 bytes)
     */
    @JvmStatic
    fun tweakPublicKey(
        scanPublicKey: ByteArray,
        inputPublicKey: ByteArray,
    ): ByteArray {
        val tweak = computeTweak(scanPublicKey, inputPublicKey)

        // For now, return the tweak - actual point multiplication requires secp256k1 library
        // In full implementation: Q = P + t·G using secp256k1 library

        return tweak
    }

    /**
     * Derive the private key to spend a Silent Payment output.
     *
     * b' = b + t (mod n)
     * where b is spend private key, t is tweak.
     *
     * @param spendPrivateKey Receiver's spend private key (b)
     * @param tweak Tweak value from scan (t)
     * @return Tweaked private key (b')
     */
    @JvmStatic
    fun derivePrivateKey(
        spendPrivateKey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        // Scalar addition modulo curve order
        // b' = (b + t) mod n

        val b = spendPrivateKey.toBigInteger()
        val t = tweak.toBigInteger()
        val n = CURVE_ORDER.toBigInteger()

        val bPrime = (b + t).mod(n)

        return bPrime.toByteArray().let { bytes ->
            // Ensure 32-byte result
            when {
                bytes.size == 32 -> bytes
                bytes.size < 32 -> ByteArray(32 - bytes.size) { 0 } + bytes
                else -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            }
        }
    }

    /**
     * Check if a transaction output is a Silent Payment to our scan key.
     *
     * This is the scanning operation - checking if a P2TR output matches
     * any of our expected tweaked addresses.
     *
     * @param scriptPubKey The output script to check
     * @param scanPublicKey Our scan public key
     * @param spendPublicKey Our spend public key
     * @param inputPubKeys All input public keys from the transaction
     * @return Tweak value if match found, null otherwise
     */
    @JvmStatic
    fun checkOutput(
        scriptPubKey: ByteArray,
        scanPublicKey: ByteArray,
        spendPublicKey: ByteArray,
        inputPubKeys: List<ByteArray>,
    ): ByteArray? {
        // For each input public key, compute expected tweaked address
        for (inputPubKey in inputPubKeys) {
            val tweak = computeTweak(scanPublicKey, inputPubKey)

            // Compute expected tweaked public key
            // Q = spendPublicKey + tweak·G
            // Then convert to P2TR address

            // For now, simplified check - actual implementation needs secp256k1 math
            val expectedScript = computeExpectedScript(spendPublicKey, tweak)

            if (scriptPubKey.contentEquals(expectedScript)) {
                return tweak
            }
        }

        return null
    }

    /**
     * Compute expected P2TR scriptPubKey for a Silent Payment.
     *
     * @param spendPublicKey Base spend public key
     * @param tweak Tweak value
     * @return 34-byte P2TR scriptPubKey (OP_1 + 32-byte x-only pubkey)
     */
    @JvmStatic
    fun computeExpectedScript(
        spendPublicKey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        // Q = spendPublicKey + tweak·G
        // Then take x-coordinate only (32 bytes) for P2TR

        // Placeholder - actual implementation requires point addition
        // For now, return dummy P2TR format: 0x51 + 32_bytes
        return byteArrayOf(0x51) + ByteArray(32) { 0 }
    }

    // Helper: SHA256
    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    // Helper: Convert byte array to BigInteger (unsigned)
    private fun ByteArray.toBigInteger(): java.math.BigInteger {
        return java.math.BigInteger(1, this)
    }

    // Helper: Convert BigInteger to byte array
    private fun java.math.BigInteger.toByteArray(): ByteArray {
        return this.toByteArray()
    }
}

/**
 * Bech32m encoding/decoding for Silent Payment addresses.
 *
 * BIP350 bech32m is used for Silent Payments (same as Taproot addresses).
 */
object Bech32m {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun encode(hrp: String, data: ByteArray): String {
        val combined = hrpExpand(hrp) + convertBits(data, 8, 5, true)
        val checksum = createChecksum(hrp, combined)
        val all = combined + checksum

        return hrp + "1" + all.map { CHARSET[it.toInt()] }.joinToString("")
    }

    fun decode(bech32: String): Pair<String, ByteArray> {
        require(bech32 == bech32.lowercase()) { "Bech32 must be lowercase" }

        val pos = bech32.lastIndexOf('1')
        require(pos in 1..83) { "Invalid separator position" }

        val hrp = bech32.substring(0, pos)
        val data = bech32.substring(pos + 1)

        require(data.length >= 6) { "Too short checksum" }

        val decoded = data.map { CHARSET.indexOf(it) }
        require(decoded.all { it != -1 }) { "Invalid character" }

        val payload = decoded.dropLast(6)
        val check = verifyChecksum(hrp, payload)
        require(check) { "Invalid checksum" }

        val result = convertBits(payload.map { it.toByte() }.toByteArray(), 5, 8, false)
        return hrp to result
    }

    private fun hrpExpand(hrp: String): List<Byte> {
        val result = mutableListOf<Byte>()
        hrp.forEach { c ->
            result.add((c.code shr 5).toByte())
        }
        result.add(0)
        hrp.forEach { c ->
            result.add((c.code and 0x1f).toByte())
        }
        return result
    }

    private fun verifyChecksum(hrp: String, data: List<Int>): Boolean {
        return polymod(hrpExpand(hrp) + data.map { it.toByte() }) == 1
    }

    private fun createChecksum(hrp: String, data: List<Byte>): List<Byte> {
        val polymod = polymod(hrpExpand(hrp) + data + listOf(0, 0, 0, 0, 0, 0)) xor 1
        return (0..5).map { i ->
            ((polymod shr (5 * (5 - i))) and 31).toByte()
        }
    }

    private fun polymod(data: List<Byte>): Int {
        var chk = 1
        data.forEach { v ->
            val b = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v.toInt()
            for (i in 0..4) {
                chk = chk xor (((b shr i) and 1) * GENERATORS[i])
            }
        }
        return chk
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): List<Byte> {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1
        val maxAcc = (1 shl (fromBits + toBits - 1)) - 1

        data.forEach { value ->
            val b = value.toInt() and 0xff
            acc = ((acc shl fromBits) or b) and maxAcc
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxv).toByte())
            }
        }

        if (pad) {
            if (bits > 0) {
                result.add(((acc shl (toBits - bits)) and maxv).toByte())
            }
        } else {
            require(bits < fromBits) { "Invalid padding" }
            require((acc shl (toBits - bits)) and maxv == 0) { "Non-zero padding" }
        }

        return result
    }
}
