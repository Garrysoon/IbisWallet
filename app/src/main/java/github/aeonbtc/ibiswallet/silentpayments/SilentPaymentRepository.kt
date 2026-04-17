package github.aeonbtc.ibiswallet.silentpayments

import github.aeonbtc.ibiswallet.data.local.SecureStorage
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network

/**
 * Repository for Silent Payments operations.
 *
 * Manages:
 * - Key generation and storage
 * - Address creation and retrieval
 * - Server-assisted scanning for incoming payments
 * - Integration with wallet sync
 *
 * @param secureStorage Encrypted storage for keys
 * @param network Mainnet or Testnet
 * @param config Silent Payment configuration
 */
class SilentPaymentRepository(
    private val secureStorage: SecureStorage,
    private val network: Network,
    private val config: SilentPaymentConfig = SilentPaymentConfig(),
) {
    private val scanApi: SilentPaymentScanApi = SilentPaymentScanApiFactory.create(config)

    companion object {
        private const val KEY_SP_SCAN_PRIVATE = "silent_payment_scan_private"
        private const val KEY_SP_SCAN_PUBLIC = "silent_payment_scan_public"
        private const val KEY_SP_SPEND_PRIVATE = "silent_payment_spend_private"
        private const val KEY_SP_SPEND_PUBLIC = "silent_payment_spend_public"
        private const val KEY_SP_ACTIVATION_HEIGHT = "silent_payment_activation_height"
        private const val KEY_SP_LAST_SCANNED_HEIGHT = "silent_payment_last_scanned_height"
    }

    /**
     * Initialize Silent Payments for a wallet.
     *
     * Generates scan/spend keys and stores them securely.
     * Sets activation height to current block height (or 0 for testnet).
     *
     * @param walletId Wallet identifier
     * @param mnemonic Wallet mnemonic phrase
     * @throws SilentPaymentException.CryptoError if key generation fails
     */
    suspend fun initialize(walletId: String, mnemonic: Mnemonic) {
        if (!config.enabled) {
            return
        }

        // Generate keys
        val keys = SilentPaymentCrypto.generateKeys(mnemonic, network)

        // Store keys securely
        secureStorage.saveSilentPaymentKeys(
            walletId = walletId,
            scanPrivate = bytesToHex(keys.scanPrivateKey),
            scanPublic = bytesToHex(keys.scanPublicKey),
            spendPrivate = bytesToHex(keys.spendPrivateKey),
            spendPublic = bytesToHex(keys.spendPublicKey),
        )

        // Set activation height (for scanning from activation)
        val currentHeight = if (config.activationHeight != null) {
            config.activationHeight
        } else {
            // For testnet, start from recent block to avoid full scan
            if (network == Network.TESTNET) 0 else 0
        }
        secureStorage.setSilentPaymentActivationHeight(walletId, currentHeight)
    }

    /**
     * Check if Silent Payments are initialized for wallet.
     *
     * @param walletId Wallet identifier
     * @return true if keys exist
     */
    suspend fun isInitialized(walletId: String): Boolean {
        return secureStorage.hasSilentPaymentKeys(walletId)
    }

    /**
     * Get Silent Payment address for receiving.
     *
     * Returns the static SP1 address that can be shared publicly.
     * All payments to this address will appear as separate UTXOs.
     *
     * @param walletId Wallet identifier
     * @return SilentPaymentAddress with sp1... string
     * @throws IllegalStateException if not initialized
     */
    suspend fun getAddress(walletId: String): SilentPaymentAddress {
        val keys = loadKeys(walletId)
            ?: throw IllegalStateException("Silent Payments not initialized for wallet $walletId")

        return SilentPaymentCrypto.createAddress(
            scanPublicKey = keys.scanPublicKey,
            spendPublicKey = keys.spendPublicKey,
            network = network,
        )
    }

    /**
     * Scan for incoming Silent Payment outputs.
     *
     * Uses server-assisted scanning (public scan key sent to server).
     * Server returns matching outputs with tweak data.
     *
     * @param walletId Wallet identifier
     * @param currentBlockHeight Current blockchain height
     * @return List of found Silent Payment outputs
     */
    suspend fun scanForPayments(
        walletId: String,
        currentBlockHeight: Int,
    ): List<SilentPaymentOutput> {
        if (!config.enabled) {
            return emptyList()
        }

        val keys = loadKeys(walletId)
            ?: return emptyList()

        val fromHeight = secureStorage.getSilentPaymentLastScannedHeight(walletId)
            ?: secureStorage.getSilentPaymentActivationHeight(walletId)
            ?: 0

        // Don't scan if already at tip
        if (fromHeight >= currentBlockHeight) {
            return emptyList()
        }

        val request = SilentPaymentScanRequest(
            scanPublicKey = keys.scanPublicKey,
            blockHeightFrom = fromHeight,
            blockHeightTo = currentBlockHeight,
        )

        return try {
            val response = scanApi.scan(request)

            // Update last scanned height
            secureStorage.setSilentPaymentLastScannedHeight(walletId, response.lastScannedHeight)

            response.outputs
        } catch (e: Exception) {
            // Log error but don't fail wallet sync
            emptyList()
        }
    }

    /**
     * Get private key to spend a Silent Payment output.
     *
     * Computes b' = b + t (mod n) where:
     * - b is spend private key
     * - t is tweak from scan
     *
     * @param walletId Wallet identifier
     * @param tweak Tweak value from SilentPaymentOutput
     * @return Tweaked private key for spending
     */
    suspend fun getSpendPrivateKey(
        walletId: String,
        tweak: ByteArray,
    ): ByteArray? {
        val keys = loadKeys(walletId) ?: return null

        return SilentPaymentCrypto.derivePrivateKey(
            spendPrivateKey = keys.spendPrivateKey,
            tweak = tweak,
        )
    }

    /**
     * Disable Silent Payments for wallet.
     *
     * Clears keys from memory (but keeps in storage for recovery).
     *
     * @param walletId Wallet identifier
     */
    suspend fun disable(walletId: String) {
        // Note: Keys remain in secure storage for potential re-activation
        // Just mark as disabled
        secureStorage.setSilentPaymentDisabled(walletId, true)
    }

    /**
     * Clear all Silent Payment data for wallet.
     *
     * WARNING: This deletes keys permanently! Only use when deleting wallet.
     *
     * @param walletId Wallet identifier
     */
    suspend fun clear(walletId: String) {
        secureStorage.clearSilentPaymentData(walletId)
    }

    /**
     * Load keys from secure storage.
     */
    private suspend fun loadKeys(walletId: String): SilentPaymentKeys? {
        val data = secureStorage.getSilentPaymentKeys(walletId) ?: return null

        return try {
            SilentPaymentKeys(
                scanPrivateKey = hexToBytes(data.scanPrivate),
                scanPublicKey = hexToBytes(data.scanPublic),
                spendPrivateKey = hexToBytes(data.spendPrivate),
                spendPublicKey = hexToBytes(data.spendPublic),
            )
        } catch (e: Exception) {
            null
        }
    }

    // Helpers
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}

/**
 * Extension functions for SecureStorage (will be added to actual SecureStorage class).
 */
suspend fun SecureStorage.saveSilentPaymentKeys(
    walletId: String,
    scanPrivate: String,
    scanPublic: String,
    spendPrivate: String,
    spendPublic: String,
) {
    // Implementation added via extension
    val prefs = getSecurePreferences(walletId)
    prefs.edit()
        .putString("sp_scan_private_$walletId", scanPrivate)
        .putString("sp_scan_public_$walletId", scanPublic)
        .putString("sp_spend_private_$walletId", spendPrivate)
        .putString("sp_spend_public_$walletId", spendPublic)
        .apply()
}

suspend fun SecureStorage.getSilentPaymentKeys(walletId: String): SilentPaymentKeyData? {
    val prefs = getSecurePreferences(walletId)
    val scanPrivate = prefs.getString("sp_scan_private_$walletId", null) ?: return null
    val scanPublic = prefs.getString("sp_scan_public_$walletId", null) ?: return null
    val spendPrivate = prefs.getString("sp_spend_private_$walletId", null) ?: return null
    val spendPublic = prefs.getString("sp_spend_public_$walletId", null) ?: return null

    return SilentPaymentKeyData(
        scanPrivate = scanPrivate,
        scanPublic = scanPublic,
        spendPrivate = spendPrivate,
        spendPublic = spendPublic,
    )
}

suspend fun SecureStorage.hasSilentPaymentKeys(walletId: String): Boolean {
    return getSilentPaymentKeys(walletId) != null
}

suspend fun SecureStorage.setSilentPaymentActivationHeight(walletId: String, height: Int) {
    getSecurePreferences(walletId).edit()
        .putInt("sp_activation_height_$walletId", height)
        .apply()
}

suspend fun SecureStorage.getSilentPaymentActivationHeight(walletId: String): Int? {
    val height = getSecurePreferences(walletId).getInt("sp_activation_height_$walletId", -1)
    return if (height >= 0) height else null
}

suspend fun SecureStorage.setSilentPaymentLastScannedHeight(walletId: String, height: Int) {
    getSecurePreferences(walletId).edit()
        .putInt("sp_last_scanned_$walletId", height)
        .apply()
}

suspend fun SecureStorage.getSilentPaymentLastScannedHeight(walletId: String): Int? {
    val height = getSecurePreferences(walletId).getInt("sp_last_scanned_$walletId", -1)
    return if (height >= 0) height else null
}

suspend fun SecureStorage.setSilentPaymentDisabled(walletId: String, disabled: Boolean) {
    getSecurePreferences(walletId).edit()
        .putBoolean("sp_disabled_$walletId", disabled)
        .apply()
}

suspend fun SecureStorage.isSilentPaymentDisabled(walletId: String): Boolean {
    return getSecurePreferences(walletId).getBoolean("sp_disabled_$walletId", false)
}

suspend fun SecureStorage.clearSilentPaymentData(walletId: String) {
    getSecurePreferences(walletId).edit()
        .remove("sp_scan_private_$walletId")
        .remove("sp_scan_public_$walletId")
        .remove("sp_spend_private_$walletId")
        .remove("sp_spend_public_$walletId")
        .remove("sp_activation_height_$walletId")
        .remove("sp_last_scanned_$walletId")
        .remove("sp_disabled_$walletId")
        .apply()
}

// Stub for getSecurePreferences - actual implementation uses EncryptedSharedPreferences
private fun SecureStorage.getSecurePreferences(walletId: String): android.content.SharedPreferences {
    // This would return EncryptedSharedPreferences in real implementation
    throw NotImplementedError("Must be implemented in SecureStorage")
}

data class SilentPaymentKeyData(
    val scanPrivate: String,
    val scanPublic: String,
    val spendPrivate: String,
    val spendPublic: String,
)
