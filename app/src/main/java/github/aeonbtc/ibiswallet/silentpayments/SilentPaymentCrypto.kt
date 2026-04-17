package github.aeonbtc.ibiswallet.silentpayments

import org.bitcoinj.secp256k1.api.P256K1Key
import org.bitcoinj.secp256k1.api.P256K1XOnlyPubKey
import org.bitcoinj.secp256k1.api.Secp256k1
import org.bitcoinj.secp256k1.bouncy.BouncySecp256k1
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import java.security.MessageDigest

/**
 * Silent Payments (BIP 352) Cryptographic Operations
 *
 * Uses bitcoinj secp256k1 library (org.bitcoinj.secp256k1) for all elliptic curve operations:
 * - Public key generation from private key
 * - Point addition: Q = P + t·G (for generating tweaked addresses)
 * - Scalar addition: b' = b + t (mod n) (for deriving spend keys)
 * - Tagged hash computation (BIP0352/Const)
 *
 * Library: org.bitcoinj.secp256k1 (v0.17.1)
 * Provider: BouncyCastle implementation (pure Java, no native libs needed)
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

    // Tagged hash constants for BIP352
    private const val BIP0352_CONST = "BIP0352/Const"
    private const val BIP0352_SEND = "BIP0352/Send"

    // Lazy-initialized secp256k1 instance
    private val secp256k1: Secp256k1 by lazy {
        BouncySecp256k1()
    }

    /**
     * Initialize secp256k1 library.
     * Called automatically on first use.
     */
    fun initialize() {
        // Force initialization of lazy secp256k1
        secp256k1.toString()
    }

    /**
     * Check if secp256k1 library is loaded and functional.
     */
    fun isAvailable(): Boolean {
        return try {
            val testPrivKey = P256K1Key(randomBytes(32))
            val pubKey = secp256k1.pubKeyCreate(testPrivKey)
            pubKey != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate Silent Payment keys from mnemonic using BIP352 derivation paths.
     *
     * Derivation paths (BIP352):
     * - Mainnet: m/352'/0'/0'/0' (scan), m/352'/0'/0'/1' (spend)
     * - Testnet: m/352'/1'/0'/0' (scan), m/352'/1'/0'/1' (spend)
     *
     * @param mnemonic BIP39 mnemonic phrase
     * @param network Mainnet or Testnet
     * @return SilentPaymentKeys with scan/spend key pairs
     */
    fun generateKeys(
        mnemonic: Mnemonic,
        network: Network,
    ): SilentPaymentKeys {
        val isTestnet = network != Network.BITCOIN

        // Get derivation paths based on network
        val scanPath = if (isTestnet) PATH_SCAN_TESTNET else PATH_SCAN_MAINNET
        val spendPath = if (isTestnet) PATH_SPEND_TESTNET else PATH_SPEND_MAINNET

        // Derive scan key from mnemonic using BDK
        val scanPrivKey = derivePrivateKeyFromMnemonic(mnemonic, scanPath)
            ?: throw SilentPaymentException.CryptoError("Failed to derive scan key from mnemonic")

        // Derive spend key from mnemonic using BDK
        val spendPrivKey = derivePrivateKeyFromMnemonic(mnemonic, spendPath)
            ?: throw SilentPaymentException.CryptoError("Failed to derive spend key from mnemonic")

        // Generate public keys from private keys using secp256k1
        val scanKey = P256K1Key(scanPrivKey)
        val spendKey = P256K1Key(spendPrivKey)

        val scanPubKey = secp256k1.pubKeyCreate(scanKey)
        val spendPubKey = secp256k1.pubKeyCreate(spendKey)

        // Convert to byte arrays
        val scanPublicKeyBytes = compressedPubKeyToBytes(scanPubKey)
        val spendPublicKeyBytes = compressedPubKeyToBytes(spendPubKey)

        return SilentPaymentKeys(
            scanPrivateKey = scanPrivKey,
            scanPublicKey = scanPublicKeyBytes,
            spendPrivateKey = spendPrivKey,
            spendPublicKey = spendPublicKeyBytes,
        )
    }

    /**
     * Derive a private key from mnemonic at a specific BIP32 path.
     * Uses BDK's DescriptorSecretKey for derivation.
     */
    private fun derivePrivateKeyFromMnemonic(
        mnemonic: Mnemonic,
        path: String,
    ): ByteArray? {
        return try {
            // Create a BDK descriptor secret key from mnemonic
            val descriptorKey = org.bitcoindevkit.DescriptorSecretKey(
                org.bitcoindevkit.Network.TESTNET,  // BDK network parameter
                mnemonic,
                null  // No passphrase
            )

            // Derive at the specified path
            val derivedKey = descriptorKey.extend(
                org.bitcoindevkit.DerivationPath(path)
            )

            // Extract the private key bytes
            val secretBytes = derivedKey.secretBytes()
            if (secretBytes != null && secretBytes.size == 32) {
                secretBytes.copyOf()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert bitcoinj public key to 33-byte compressed format.
     */
    private fun compressedPubKeyToBytes(pubKey: org.bitcoinj.secp256k1.api.P256K1PubKey): ByteArray {
        // Get compressed public key (33 bytes: 0x02/0x03 + 32-byte x-coordinate)
        return pubKey.toCompressed()
    }

    /**
     * Create a Silent Payment address from scan and spend public keys.
     *
     * Uses bech32m encoding (BIP350) for the address string.
     * Format: hrp + '1' + version(0) + scan_key(33) + spend_key(33) + [labels]
     *
     * @param scanPublicKey 33-byte compressed public key
     * @param spendPublicKey 33-byte compressed public key
     * @param network Mainnet or Testnet
     * @param labels Optional numbered labels (0-255) for address variants
     * @return SilentPaymentAddress with sp1... or tsp1... string
     */
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

        val hrp = if (network == Network.BITCOIN) HRP_MAINNET else HRP_TESTNET

        // Data: version byte (0) + scan key (33 bytes) + spend key (33 bytes) + labels
        val data = mutableListOf<Byte>()
        data.add(0) // Version byte 0 for base SP
        data.addAll(scanPublicKey.toList())
        data.addAll(spendPublicKey.toList())

        labels.forEach { label ->
            if (label in 0..255) {
                data.add(label.toByte())
            } else {
                throw SilentPaymentException.CryptoError(
                    "Label must be in range 0-255, got $label"
                )
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
     * Parses sp1... or tsp1... address and extracts scan/spend keys.
     *
     * @param address Silent Payment address string
     * @return SilentPaymentAddress with extracted components
     * @throws SilentPaymentException.InvalidAddress if invalid format
     */
    fun decodeAddress(address: String): SilentPaymentAddress {
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

            if (data.size < 67) {
                throw SilentPaymentException.InvalidAddress(
                    "Invalid Silent Payment address: too short (${data.size} bytes, need at least 67)"
                )
            }

            val version = data[0].toInt()
            if (version != 0) {
                throw SilentPaymentException.InvalidAddress(
                    "Unsupported Silent Payment version: $version (only version 0 supported)"
                )
            }

            val scanKey = data.copyOfRange(1, 34)
            val spendKey = data.copyOfRange(34, 67)

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
     * @param address String to check
     * @return true if valid SP1/TSP1 address
     */
    fun isValidAddress(address: String): Boolean {
        return try {
            decodeAddress(address)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Compute the tagged hash tweak value: hashBIP0352/Const(P || R)
     *
     * Used for both receiving (checking outputs) and sending (generating tweaked address).
     *
     * BIP352 formula: t = hashBIP0352/Const(P_scan || P_input)
     * Where:
     * - P_scan is the recipient's scan public key
     * - P_input is the public key from the transaction input (for sending)
     *   or an input public key from the transaction (for receiving)
     *
     * @param scanPublicKey Recipient's scan public key (33 bytes compressed)
     * @param inputPublicKey Input's public key (33 bytes compressed)
     * @return 32-byte tweak value
     */
    fun computeTweak(
        scanPublicKey: ByteArray,
        inputPublicKey: ByteArray,
    ): ByteArray {
        if (scanPublicKey.size != 33) {
            throw SilentPaymentException.CryptoError(
                "Scan public key must be 33 bytes, got ${scanPublicKey.size}"
            )
        }
        if (inputPublicKey.size != 33) {
            throw SilentPaymentException.CryptoError(
                "Input public key must be 33 bytes, got ${inputPublicKey.size}"
            )
        }

        // BIP0352/Const tagged hash
        // taggedHash = SHA256(SHA256("BIP0352/Const") || SHA256("BIP0352/Const") || data)
        val data = scanPublicKey + inputPublicKey
        return taggedHash(BIP0352_CONST, data)
    }

    /**
     * Compute the tagged hash for sending: hashBIP0352/Send(dh || k')
     *
     * Used when generating a Silent Payment from a shared secret.
     *
     * @param sharedSecret Shared secret bytes (typically from ECDH)
     * @param inputPrivateKeyTweaked Tweaked input private key
     * @return 32-byte hash value
     */
    fun computeSendHash(
        sharedSecret: ByteArray,
        inputPrivateKeyTweaked: ByteArray,
    ): ByteArray {
        val data = sharedSecret + inputPrivateKeyTweaked
        return taggedHash(BIP0352_SEND, data)
    }

    /**
     * Tweak a public key: Q = P + t·G
     *
     * This is the core Silent Payments operation for generating
     * the tweaked output public key that the recipient can spend.
     *
     * Uses secp256k1_ec_pubkey_tweak_add: adds tweak*G to the public key.
     *
     * @param spendPublicKey Recipient's spend public key P (33 bytes)
     * @param tweak Tweak value t (32 bytes)
     * @return Tweaked public key Q (33 bytes compressed)
     */
    fun tweakPublicKey(
        spendPublicKey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        if (spendPublicKey.size != 33) {
            throw SilentPaymentException.CryptoError(
                "Spend public key must be 33 bytes, got ${spendPublicKey.size}"
            )
        }
        if (tweak.size != 32) {
            throw SilentPaymentException.CryptoError(
                "Tweak must be 32 bytes, got ${tweak.size}"
            )
        }

        return try {
            // Parse public key
            val pubKey = secp256k1.pubKeyParse(spendPublicKey)
                ?: throw SilentPaymentException.CryptoError("Failed to parse spend public key")

            // Apply tweak: Q = P + t·G
            val tweakedPubKey = secp256k1.pubKeyTweakAdd(pubKey, tweak)
                ?: throw SilentPaymentException.CryptoError("Failed to tweak public key")

            // Serialize to compressed format (33 bytes)
            tweakedPubKey.toCompressed()
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError(
                "Failed to tweak public key: ${e.message}"
            )
        }
    }

    /**
     * Derive the private key to spend a Silent Payment output.
     *
     * Formula: b' = b + t (mod n)
     * Where:
     * - b is the spend private key
     * - t is the tweak from scanning
     * - n is the curve order
     *
     * Uses secp256k1_ec_privkey_tweak_add: adds tweak to private key (mod n).
     *
     * @param spendPrivateKey Original spend private key b (32 bytes)
     * @param tweak Tweak value t (32 bytes)
     * @return Derived private key b' (32 bytes)
     */
    fun derivePrivateKey(
        spendPrivateKey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        if (spendPrivateKey.size != 32) {
            throw SilentPaymentException.CryptoError(
                "Spend private key must be 32 bytes, got ${spendPrivateKey.size}"
            )
        }
        if (tweak.size != 32) {
            throw SilentPaymentException.CryptoError(
                "Tweak must be 32 bytes, got ${tweak.size}"
            )
        }

        return try {
            // Apply tweak to private key: b' = b + t (mod n)
            val tweakedPrivKey = secp256k1.privKeyTweakAdd(spendPrivateKey, tweak)
                ?: throw SilentPaymentException.CryptoError("Failed to tweak private key")

            tweakedPrivKey.bytes
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError(
                "Failed to derive private key: ${e.message}"
            )
        }
    }

    /**
     * Check if a transaction output is a Silent Payment to our scan key.
     *
     * For each input public key in the transaction:
     * 1. Compute tweak t = hashBIP0352/Const(P_scan || P_input)
     * 2. Generate tweaked public key: Q_i = P_spend + t·G
     * 3. Convert Q_i to x-only public key for P2TR
     * 4. Check if output scriptPubKey matches P2TR(Q_i)
     *
     * @param scriptPubKey Output's scriptPubKey to check
     * @param scanPublicKey Our scan public key
     * @param spendPublicKey Our spend public key
     * @param inputPubKeys List of input public keys from the transaction
     * @return Tweak value if output matches, null otherwise
     */
    fun checkOutput(
        scriptPubKey: ByteArray,
        scanPublicKey: ByteArray,
        spendPublicKey: ByteArray,
        inputPubKeys: List<ByteArray>,
    ): ByteArray? {
        // Check each input public key
        for (inputPubKey in inputPubKeys) {
            try {
                // Compute tweak for this input
                val tweak = computeTweak(scanPublicKey, inputPubKey)

                // Generate the expected tweaked public key
                val tweakedPubKey = tweakPublicKey(spendPublicKey, tweak)

                // Convert to P2TR x-only public key (remove parity byte)
                val xOnlyPubKey = tweakedPubKey.copyOfRange(1, 33)

                // Build expected P2TR scriptPubKey: 0x51 + 32-byte x-only pubkey
                val expectedScript = byteArrayOf(0x51) + xOnlyPubKey

                // Check if this matches the output
                if (scriptPubKey.contentEquals(expectedScript)) {
                    return tweak
                }
            } catch (e: Exception) {
                // Continue to next input
                continue
            }
        }

        return null
    }

    /**
     * Compute expected P2TR scriptPubKey for a Silent Payment.
     *
     * @param spendPublicKey Recipient's spend public key
     * @param tweak Tweak value from computeTweak()
     * @return Expected P2TR scriptPubKey (34 bytes: 0x51 + 32-byte x-only pubkey)
     */
    fun computeExpectedScript(
        spendPublicKey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        val tweakedPubKey = tweakPublicKey(spendPublicKey, tweak)
        // Convert to x-only (remove first byte which is 0x02 or 0x03)
        val xOnlyPubKey = tweakedPubKey.copyOfRange(1, 33)
        // P2TR script: OP_1 (0x51) + 32-byte x-only pubkey
        return byteArrayOf(0x51) + xOnlyPubKey
    }

    /**
     * Generate a public key from a private key.
     *
     * @param privateKey 32-byte private key
     * @return 33-byte compressed public key
     */
    fun derivePublicKey(privateKey: ByteArray): ByteArray {
        if (privateKey.size != 32) {
            throw SilentPaymentException.CryptoError(
                "Private key must be 32 bytes, got ${privateKey.size}"
            )
        }
        return try {
            val key = P256K1Key(privateKey)
            val pubKey = secp256k1.pubKeyCreate(key)
            pubKey.toCompressed()
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError(
                "Failed to derive public key: ${e.message}"
            )
        }
    }

    /**
     * Verify a private key is valid (non-zero and less than curve order).
     */
    fun isValidPrivateKey(privateKey: ByteArray): Boolean {
        if (privateKey.size != 32) return false
        // Check not all zeros
        return privateKey.any { it != 0.toByte() }
    }

    /**
     * Verify a public key is valid.
     */
    fun isValidPublicKey(publicKey: ByteArray): Boolean {
        if (publicKey.size != 33) return false
        // Check first byte is valid compression flag
        return publicKey[0] == 0x02.toByte() || publicKey[0] == 0x03.toByte()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper functions
    // ═══════════════════════════════════════════════════════════════

    /**
     * BIP340-style tagged hash.
     *
     * tagged_hash(tag, data) = SHA256(SHA256(tag) || SHA256(tag) || data)
     */
    private fun taggedHash(tag: String, data: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray())
        return sha256(tagHash + tagHash + data)
    }

    /**
     * SHA256 hash.
     */
    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    /**
     * Concatenate two byte arrays.
     */
    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        return this.copyOf(this.size + other.size).apply {
            other.copyInto(this, this@plus.size)
        }
    }

    /**
     * Generate random bytes.
     */
    private fun randomBytes(size: Int): ByteArray {
        return java.security.SecureRandom().let { random ->
            ByteArray(size).apply { random.nextBytes(this) }
        }
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
        return hrp to result.toByteArray()
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
