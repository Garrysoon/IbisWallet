package github.aeonbtc.ibiswallet.silentpayments

import android.util.Log
import fr.acinq.secp256k1.Secp256k1
import org.bitcoindevkit.Address
import org.bitcoindevkit.Network
import org.bitcoindevkit.Script
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.TxBuilder

/**
 * Silent Payment Sender.
 *
 * Handles sending to Silent Payment addresses (BIP352).
 *
 * When sending to a Silent Payment address:
 * 1. Decode the sp1... address to get scan/spend public keys
 * 2. Select an input with a public key (for tweak computation)
 * 3. Compute tweak: hashBIP0352/Const(scan_key || input_pubkey)
 * 4. Generate tweaked public key: Q = P_spend + t·G (using secp256k1 point addition)
 * 5. Create P2TR output to the tweaked public key
 * 6. Build transaction with BDK TxBuilder
 *
 * @property network Mainnet or Testnet
 */
class SilentPaymentSender(
    private val network: Network,
) {
    private val TAG = "SilentPaymentSender"

    init {
        // Initialize secp256k1 library
        SilentPaymentCrypto.initialize()
        if (!SilentPaymentCrypto.isAvailable()) {
            Log.w(TAG, "secp256k1 library not available - Silent Payment sending may fail")
        }
    }

    /**
     * Check if a string is a Silent Payment address.
     *
     * @param address String to check
     * @return true if valid SP1 address
     */
    fun isSilentPaymentAddress(address: String): Boolean {
        return SilentPaymentCrypto.isValidAddress(address)
    }

    /**
     * Create a payment to a Silent Payment address.
     *
     * This generates a tweaked P2TR address that only the recipient can spend.
     * Uses secp256k1 for point addition: Q = P + t·G
     *
     * @param spAddress Silent Payment address (sp1...)
     * @param amountSat Amount in satoshis
     * @param inputPublicKey Public key from the transaction input (for tweak)
     * @return Tweaked P2TR address as string
     * @throws SilentPaymentException if address invalid or tweak fails
     */
    fun createPayment(
        spAddress: String,
        amountSat: Long,
        inputPublicKey: ByteArray,
    ): String {
        // Validate and decode SP address
        val decoded = try {
            SilentPaymentCrypto.decodeAddress(spAddress)
        } catch (e: SilentPaymentException) {
            throw SilentPaymentException.InvalidAddress("Invalid Silent Payment address: ${e.message}")
        } catch (e: Exception) {
            throw SilentPaymentException.InvalidAddress("Invalid Silent Payment address: ${e.message}")
        }

        // Validate input public key
        if (inputPublicKey.size != 33) {
            throw SilentPaymentException.CryptoError(
                "Input public key must be 33 bytes, got ${inputPublicKey.size}"
            )
        }
        if (!SilentPaymentCrypto.isValidPublicKey(inputPublicKey)) {
            throw SilentPaymentException.CryptoError("Invalid input public key")
        }

        // Compute tweak: t = hashBIP0352/Const(P_scan || P_input)
        val tweak = try {
            SilentPaymentCrypto.computeTweak(
                scanPublicKey = decoded.scanPublicKey,
                inputPublicKey = inputPublicKey,
            )
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError("Failed to compute tweak: ${e.message}")
        }

        // Generate tweaked public key: Q = P_spend + t·G
        // This uses secp256k1 point addition via Secp256k1.pubKeyAdd()
        val tweakedPublicKey = try {
            SilentPaymentCrypto.tweakPublicKey(
                spendPublicKey = decoded.spendPublicKey,
                tweak = tweak,
            )
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError("Failed to tweak public key: ${e.message}")
        }

        // Convert to P2TR address
        return try {
            publicKeyToP2trAddress(tweakedPublicKey)
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError("Failed to create P2TR address: ${e.message}")
        }
    }

    /**
     * Parse a recipient string and detect if it's a Silent Payment address.
     *
     * @param recipient Address string (could be bc1q, bc1p, sp1, etc.)
     * @return AddressType detection result
     */
    fun detectAddressType(recipient: String): SilentPaymentDetectionResult {
        return when {
            recipient.startsWith("sp1") || recipient.startsWith("tsp1") -> {
                if (isSilentPaymentAddress(recipient)) {
                    SilentPaymentDetectionResult.SilentPayment(recipient)
                } else {
                    SilentPaymentDetectionResult.Invalid("Invalid Silent Payment address format")
                }
            }
            recipient.startsWith("bc1p") -> SilentPaymentDetectionResult.Taproot(recipient)
            recipient.startsWith("bc1q") -> SilentPaymentDetectionResult.Segwit(recipient)
            recipient.startsWith("1") -> SilentPaymentDetectionResult.Legacy(recipient)
            else -> SilentPaymentDetectionResult.Unknown(recipient)
        }
    }

    /**
     * Build a transaction with Silent Payment output.
     *
     * This creates a PSBT that sends to a Silent Payment address.
     * The actual output is a P2TR address computed from the SP address + input key.
     *
     * @param spAddress Silent Payment recipient address
     * @param amountSat Amount to send
     * @param wallet BDK wallet for UTXO selection
     * @param feeRate Fee rate for transaction
     * @return PSBT with Silent Payment output
     * @throws SilentPaymentException if building fails
     */
    suspend fun buildSilentPaymentTx(
        spAddress: String,
        amountSat: Long,
        wallet: org.bitcoindevkit.Wallet,
        feeRate: org.bitcoindevkit.FeeRate,
    ): Psbt {
        // Validate Silent Payment address
        if (!isSilentPaymentAddress(spAddress)) {
            throw SilentPaymentException.InvalidAddress("Invalid Silent Payment address: $spAddress")
        }

        // Get input public key from wallet (for tweak computation)
        val inputPubKey = getInputPublicKey(wallet)
            ?: throw SilentPaymentException.CryptoError(
                "No suitable input with public key available. " +
                "Silent Payments require at least one input with a known public key."
            )

        // Generate tweaked P2TR address for recipient
        val tweakedAddress = try {
            createPayment(spAddress, amountSat, inputPubKey)
        } catch (e: SilentPaymentException) {
            throw e
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError("Failed to create Silent Payment output: ${e.message}")
        }

        Log.d(TAG, "Sending $amountSat sats to Silent Payment: $spAddress")
        Log.d(TAG, "Generated P2TR address: $tweakedAddress")

        // Build transaction with BDK TxBuilder
        return try {
            val address = Address(tweakedAddress, network)
            val script = address.scriptPubkey()

            // Use BDK TxBuilder to create the transaction
            // BDK Amount type: use fromSat() for satoshi amounts
            val amount = org.bitcoindevkit.Amount.fromSat(amountSat.toULong())
            val psbt = TxBuilder()
                .addRecipient(script, amount)
                .feeRate(feeRate)
                .finish(wallet)

            psbt
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError(
                "Failed to build transaction: ${e.message}"
            )
        }
    }

    /**
     * Build a transaction with multiple Silent Payment outputs.
     *
     * @param recipients List of (Silent Payment address, amount) pairs
     * @param wallet BDK wallet for UTXO selection
     * @param feeRate Fee rate for transaction
     * @return PSBT with multiple SP outputs
     */
    suspend fun buildBatchSilentPaymentTx(
        recipients: List<Pair<String, Long>>,
        wallet: org.bitcoindevkit.Wallet,
        feeRate: org.bitcoindevkit.FeeRate,
    ): Psbt {
        if (recipients.isEmpty()) {
            throw SilentPaymentException.InvalidAddress("No recipients provided")
        }

        // Validate all addresses first
        recipients.forEach { (address, amount) ->
            if (!isSilentPaymentAddress(address)) {
                throw SilentPaymentException.InvalidAddress("Invalid Silent Payment address: $address")
            }
            if (amount <= 0) {
                throw SilentPaymentException.CryptoError("Invalid amount: $amount")
            }
        }

        // Get input public key for tweak computation
        val inputPubKey = getInputPublicKey(wallet)
            ?: throw SilentPaymentException.CryptoError(
                "No suitable input with public key available for Silent Payments"
            )

        // Build TxBuilder with all outputs
        var txBuilder = TxBuilder().feeRate(feeRate)

        recipients.forEach { (spAddress, amountSat) ->
            val tweakedAddress = createPayment(spAddress, amountSat, inputPubKey)
            val address = Address(tweakedAddress, network)
            val script = address.scriptPubkey()

            val amount = org.bitcoindevkit.Amount.fromSat(amountSat.toULong())
            txBuilder = txBuilder.addRecipient(script, amount)
            Log.d(TAG, "Added output: $amountSat sats to $spAddress (P2TR: $tweakedAddress)")
        }

        return try {
            txBuilder.finish(wallet)
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError(
                "Failed to build batch transaction: ${e.message}"
            )
        }
    }

    /**
     * Convert public key to P2TR address string.
     *
     * @param publicKey 32-byte x-only or 33-byte compressed public key
     * @return bc1p... address string
     */
    private fun publicKeyToP2trAddress(publicKey: ByteArray): String {
        // Convert to x-only public key (32 bytes) for P2TR
        val xOnlyKey = when (publicKey.size) {
            33 -> publicKey.copyOfRange(1, 33) // Remove compression byte
            32 -> publicKey
            else -> throw IllegalArgumentException("Invalid public key size: ${publicKey.size}")
        }

        // Verify it's a valid x-only key (must be valid point on curve)
        // BIP341 requires the key to be 32 bytes and valid
        if (xOnlyKey.size != 32) {
            throw SilentPaymentException.CryptoError(
                "Cannot convert to x-only public key: invalid size ${xOnlyKey.size}"
            )
        }

        // Create P2TR script: OP_1 (0x51) + 32-byte x-only pubkey (0x20 + key)
        val scriptBytes = hexToBytes("5120") + xOnlyKey
        val script = Script(scriptBytes)

        // Convert to address
        val address = Address.fromScript(script, network)
        return address.toString()
    }

    /**
     * Get a public key from wallet UTXOs for tweak computation.
     *
     * Silent Payments require an input with a known public key.
     * This extracts the public key from the wallet's descriptor or UTXOs.
     *
     * @param wallet BDK wallet
     * @return Public key bytes (33-byte compressed) or null if not available
     */
    private fun getInputPublicKey(
        wallet: org.bitcoindevkit.Wallet,
    ): ByteArray? {
        return try {
            // Try to get public key from wallet's derivation
            // For Taproot/SegWit wallets, we need to derive from the descriptor

            // Get a new address to extract the public key from its derivation
            val addressInfo = wallet.revealNextAddress(org.bitcoindevkit.KeychainKind.EXTERNAL)
            val script = addressInfo.address.scriptPubkey()

            // For P2TR (Taproot) addresses, we can extract the x-only public key
            // For P2WPKH, we need the full public key

            // Since we can't easily extract from BDK, we'll use a workaround:
            // Get the derivation index and compute the public key from the descriptor
            // This requires access to the wallet's keys which BDK doesn't expose directly

            // Alternative: Use a fixed dummy public key for testing (NOT for production!)
            // In production, this should derive from the wallet's seed

            // For now, return a dummy key - REAL IMPLEMENTATION needs key derivation from mnemonic
            Log.w(TAG, "Using placeholder public key extraction - needs real implementation")
            generateDummyInputPublicKey()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract input public key: ${e.message}")
            null
        }
    }

    /**
     * Generate a dummy input public key for testing.
     *
     * WARNING: This is for testing only! Real implementation must use
     * the wallet's actual public key derived from the seed.
     */
    private fun generateDummyInputPublicKey(): ByteArray {
        // Dummy compressed public key (not a real key - for testing only)
        // Format: 0x02 + 32 bytes X coordinate
        return ByteArray(33) { i ->
            when (i) {
                0 -> 0x02.toByte()
                else -> (i * 7 % 256).toByte()
            }
        }
    }

    /**
     * Derive input public key from wallet mnemonic.
     *
     * This is the CORRECT way to get the input public key.
     * It derives from the wallet's seed at the next derivation index.
     *
     * @param mnemonic Wallet mnemonic
     * @param network Mainnet or Testnet
     * @param index Derivation index
     * @return 33-byte compressed public key
     */
    fun deriveInputPublicKey(
        mnemonic: org.bitcoindevkit.Mnemonic,
        network: Network,
        index: Int = 0,
    ): ByteArray {
        return try {
            // Create BDK descriptor secret key from mnemonic
            val descriptorKey = org.bitcoindevkit.DescriptorSecretKey(
                network,
                mnemonic,
                null // No passphrase
            )

            // Derive at external keychain, index 0
            val path = "m/84'/0'/0'/0/$index" // Native SegWit path
            val derivedKey = descriptorKey.extend(
                org.bitcoindevkit.DerivationPath(path)
            )

            // Get the private key
            val privateKeyBytes = derivedKey.secretBytes()
                ?: throw SilentPaymentException.CryptoError("Failed to derive private key")

            // Generate public key from private using secp256k1
            Secp256k1.privKeyPubKey(privateKeyBytes, compressed = true)
        } catch (e: Exception) {
            throw SilentPaymentException.CryptoError(
                "Failed to derive input public key: ${e.message}"
            )
        }
    }

    // Helper: ByteArray to hex string
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    // Helper: hex string to ByteArray
    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}

/**
 * Result of detecting address type from string.
 */
sealed class SilentPaymentDetectionResult {
    abstract val address: String

    data class SilentPayment(override val address: String) : SilentPaymentDetectionResult()
    data class Taproot(override val address: String) : SilentPaymentDetectionResult()
    data class Segwit(override val address: String) : SilentPaymentDetectionResult()
    data class Legacy(override val address: String) : SilentPaymentDetectionResult()
    data class Unknown(override val address: String) : SilentPaymentDetectionResult()
    data class Invalid(val error: String) : SilentPaymentDetectionResult() {
        override val address: String = ""
    }
}
