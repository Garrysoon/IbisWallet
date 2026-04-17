package github.aeonbtc.ibiswallet.silentpayments

import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security

/**
 * Silent Payments (BIP 352) Cryptographic Operations
 *
 * Uses BouncyCastle (org.bouncycastle) for all elliptic curve operations:
 * - Public key generation from private key: P = d·G
 * - Point addition: Q = P + t·G (for generating tweaked addresses)
 * - Scalar addition: b' = b + t (mod n) (for deriving spend keys)
 * - Tagged hash computation (BIP0352/Const)
 *
 * BouncyCastle is included in Android and the JVM, no extra dependencies needed.
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

    // secp256k1 curve parameters (BouncyCastle)
    private val ecParams: ECDomainParameters by lazy {
        // Ensure BouncyCastle provider is registered
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        // secp256k1 parameters from SEC 2
        val p = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
        val a = BigInteger.ZERO
        val b = BigInteger("7", 16)
        val n = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
        val h = BigInteger.ONE
        val gX = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
        val gY = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)

        val curve = ECCurve.Fp(p, a, b, n, h)
        val g = curve.createPoint(gX, gY)
        ECDomainParameters(curve, g, n, h)
    }

    private val curveOrder: BigInteger by lazy { ecParams.n }

    /**
     * Initialize secp256k1 library.
     * BouncyCastle is auto-initialized on first use.
     */
    fun initialize() {
        // Force initialization of lazy ecParams
        ecParams.toString()
    }

    /**
     * Check if secp256k1 library is loaded and functional.
     */
    fun isAvailable(): Boolean {
        return try {
            // Test with a simple operation
            val testPrivKey = BigInteger.ONE
            val pubKey = scalarMultiplyBase(testPrivKey)
            pubKey != null && !pubKey.isInfinity
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
        val scanPubKey = derivePublicKey(scanPrivKey)
        val spendPubKey = derivePublicKey(spendPrivKey)

        return SilentPaymentKeys(
            scanPrivateKey = scanPrivKey,
            scanPublicKey = scanPubKey,
            spendPrivateKey = spendPrivKey,
            spendPublicKey = spendPubKey,
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
     * Generate a public key from a private key.
     * P = d·G where d is private key and G is generator point.
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

        val d = BigInteger(1, privateKey)
        if (d <= BigInteger.ZERO || d >= curveOrder) {
            throw SilentPaymentException.CryptoError("Invalid private key (out of range)")
        }

        val point = scalarMultiplyBase(d)
            ?: throw SilentPaymentException.CryptoError("Failed to generate public key")

        return ecPointToCompressedBytes(point)
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
     * BIP352: Q = P_spend + t·G
     * - P_spend is the recipient's spend public key
     * - t is the tweak from hash(P_scan || P_input)
     * - G is the generator point
     *
     * @param spendPublicKey Recipient's spend public key P (33 bytes compressed)
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
            // Parse spend public key (P)
            val pPoint = compressedBytesToECPoint(spendPublicKey)
                ?: throw SilentPaymentException.CryptoError("Failed to parse spend public key")

            // Compute t·G (scalar multiplication)
            val tScalar = BigInteger(1, tweak)
            val tG = scalarMultiplyBase(tScalar)
                ?: throw SilentPaymentException.CryptoError("Failed to compute t·G")

            // Q = P + t·G (point addition)
            val qPoint = pPoint.add(tG)
                .normalize()

            // Convert back to compressed bytes
            ecPointToCompressedBytes(qPoint)
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
            // b' = b + t (mod n)
            val b = BigInteger(1, spendPrivateKey)
            val t = BigInteger(1, tweak)
            val n = curveOrder

            val bPrime = b.add(t).mod(n)

            // Convert to 32-byte array
            val result = ByteArray(32)
            val bytes = bPrime.toByteArray()

            // Copy to result (handle leading zero in BigInteger representation)
            val srcOffset = if (bytes.size > 32) bytes.size - 32 else 0
            val destOffset = 32 - (bytes.size - srcOffset)
            val length = bytes.size - srcOffset

            System.arraycopy(bytes, srcOffset, result, destOffset, length)
            result
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
     * Verify a private key is valid (non-zero and less than curve order).
     */
    fun isValidPrivateKey(privateKey: ByteArray): Boolean {
        if (privateKey.size != 32) return false
        val d = BigInteger(1, privateKey)
        return d > BigInteger.ZERO && d < curveOrder
    }

    /**
     * Verify a public key is valid.
     */
    fun isValidPublicKey(publicKey: ByteArray): Boolean {
        if (publicKey.size != 33) return false
        // Check first byte is valid compression flag
        if (publicKey[0] != 0x02.toByte() && publicKey[0] != 0x03.toByte()) return false
        // Try to parse
        return try {
            compressedBytesToECPoint(publicKey) != null
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BouncyCastle helper functions
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scalar multiplication: d·G
     * Multiply generator point by scalar d.
     */
    private fun scalarMultiplyBase(d: BigInteger): ECPoint? {
        return ecParams.g.multiply(d)
    }

    /**
     * Convert compressed bytes (33 bytes: 0x02/0x03 + 32-byte x) to ECPoint.
     */
    private fun compressedBytesToECPoint(bytes: ByteArray): ECPoint? {
        if (bytes.size != 33) return null

        val yIsEven = bytes[0] == 0x02.toByte()
        val yIsOdd = bytes[0] == 0x03.toByte()
        if (!yIsEven && !yIsOdd) return null

        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        return try {
            val curve = ecParams.curve as ECCurve.Fp
            val p = curve.q

            // y² = x³ + 7 (mod p)
            val xCubed = x.modPow(BigInteger.valueOf(3), p)
            val seven = BigInteger.valueOf(7)
            val ySquared = xCubed.add(seven).mod(p)

            // y = sqrt(y²)
            val y = modSqrt(ySquared, p)
                ?: return null

            // Choose correct y based on parity
            val yFinal = if (y.testBit(0) == yIsOdd) y else p.subtract(y)

            curve.createPoint(x, yFinal)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compute modular square root: x such that x² ≡ n (mod p)
     * Using Tonelli-Shanks algorithm for prime p ≡ 3 (mod 4).
     * For secp256k1, p ≡ 3 (mod 4), so sqrt(n) = n^((p+1)/4) mod p.
     */
    private fun modSqrt(n: BigInteger, p: BigInteger): BigInteger? {
        // secp256k1: p ≡ 3 (mod 4), use simple formula
        // sqrt(n) = n^((p+1)/4) mod p
        val exponent = p.add(BigInteger.ONE).shiftRight(2) // (p+1)/4
        val sqrt = n.modPow(exponent, p)

        // Verify
        return if (sqrt.multiply(sqrt).mod(p) == n) {
            sqrt
        } else {
            null // No square root exists
        }
    }

    /**
     * Convert ECPoint to compressed bytes (33 bytes: 0x02/0x03 + 32-byte x).
     */
    private fun ecPointToCompressedBytes(point: ECPoint): ByteArray {
        val affineX = point.affineXCoord.toBigInteger()
        val affineY = point.affineYCoord.toBigInteger()

        val result = ByteArray(33)
        // First byte: 0x02 if y is even, 0x03 if y is odd
        result[0] = if (affineY.testBit(0)) 0x03.toByte() else 0x02.toByte()

        // Copy x coordinate (32 bytes, big-endian)
        val xBytes = affineX.toByteArray()
        val xOffset = if (xBytes.size > 32) xBytes.size - 32 else 0
        val xLength = xBytes.size - xOffset
        System.arraycopy(xBytes, xOffset, result, 33 - xLength, xLength)

        result
    }

    // ═══════════════════════════════════════════════════════════════
    // Hash helper functions
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
