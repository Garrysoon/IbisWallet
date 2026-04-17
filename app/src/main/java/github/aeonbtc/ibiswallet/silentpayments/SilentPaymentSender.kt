package github.aeonbtc.ibiswallet.silentpayments

import android.util.Log
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
 * 3. Compute tweak: hash(scan_key || input_pubkey)
 * 4. Generate tweaked address: P2TR with tweaked spend key
 * 5. Create transaction to the tweaked P2TR output
 *
 * @property network Mainnet or Testnet
 */
class SilentPaymentSender(
    private val network: Network,
) {
    private val TAG = "SilentPaymentSender"

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
        }

        // Compute tweak
        val tweak = SilentPaymentCrypto.computeTweak(
            scanPublicKey = decoded.scanPublicKey,
            inputPublicKey = inputPublicKey,
        )

        // Generate tweaked public key (spend key + tweak)
        // Q = P_spend + t·G
        // For now, this is a placeholder - actual implementation needs secp256k1 math
        val tweakedPublicKey = generateTweakedPublicKey(
            spendPublicKey = decoded.spendPublicKey,
            tweak = tweak,
        )

        // Convert to P2TR address
        return publicKeyToP2trAddress(tweakedPublicKey)
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
     * This is a high-level helper that integrates with BDK TxBuilder.
     *
     * @param spAddress Silent Payment recipient address
     * @param amountSat Amount to send
     * @param wallet BDK wallet for UTXO selection
     * @return PSBT with Silent Payment output
     */
    suspend fun buildSilentPaymentTx(
        spAddress: String,
        amountSat: Long,
        wallet: org.bitcoindevkit.Wallet,
        feeRate: org.bitcoindevkit.FeeRate,
    ): Psbt {
        // Get input public key from wallet (for tweak computation)
        val inputPubKey = getInputPublicKey(wallet)
            ?: throw SilentPaymentException.CryptoError("No suitable input with public key available")

        // Generate tweaked P2TR address for recipient
        val tweakedAddress = createPayment(spAddress, amountSat, inputPubKey)

        // Build transaction with BDK
        val address = Address(tweakedAddress, network)
        val script = address.scriptPubkey()

        // Create and return PSBT
        // Note: Full implementation needs proper UTXO selection, change output, etc.
        // This is simplified - real implementation would use TxBuilder properly
        throw NotImplementedError("Full Silent Payment transaction building not yet implemented")
    }

    /**
     * Generate tweaked public key: Q = P + t·G
     *
     * @param spendPublicKey Recipient's spend public key (P)
     * @param tweak Tweak value (t)
     * @return Tweaked public key bytes (33-byte compressed)
     */
    private fun generateTweakedPublicKey(
        spendPublicKey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        // Placeholder - needs secp256k1 point addition
        // Q = P + t·G
        // 1. Compute t·G (scalar mult of generator by tweak)
        // 2. Add to spendPublicKey (point addition)

        // For now return spend key (not actually tweaked)
        // TODO: Implement with secp256k1 library
        Log.w(TAG, "Tweak generation not fully implemented - using unmodified key")
        return spendPublicKey
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

        // Create P2TR script
        val script = Script.fromHex("5120" + xOnlyKey.toHex())

        // Convert to address
        val address = Address.fromScript(script, network)
        return address.toString()
    }

    /**
     * Get a public key from wallet UTXOs for tweak computation.
     *
     * Silent Payments require an input with a known public key.
     * This selects the first suitable UTXO and extracts its public key.
     *
     * @param wallet BDK wallet
     * @return Public key bytes or null if no suitable input
     */
    private fun getInputPublicKey(
        wallet: org.bitcoindevkit.Wallet,
    ): ByteArray? {
        // Get unspent outputs
        val utxos = wallet.listUnspent()

        // Find first UTXO that we can extract public key from
        // This requires knowing the derivation path and original public key
        // For Taproot/SegWit wallets, we need to track which key was used

        // TODO: Implement proper public key extraction from wallet UTXOs
        // This requires access to descriptor or key derivation info

        // Placeholder - return null for now
        return null
    }

    // Helper: ByteArray to hex string
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
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
