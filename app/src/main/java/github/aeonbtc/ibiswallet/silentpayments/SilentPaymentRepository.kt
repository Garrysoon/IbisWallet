package github.aeonbtc.ibiswallet.silentpayments

import android.util.Base64
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

        // Store keys securely using the new SecureStorage methods
        secureStorage.saveSilentPaymentScanKey(walletId, keys.scanPrivateKey)
        secureStorage.saveSilentPaymentSpendKey(walletId, keys.spendPrivateKey)

        // Mark as enabled
        secureStorage.setSilentPaymentEnabled(walletId, true)

        // Set activation height (for scanning from activation)
        val currentHeight = config.activationHeight ?: 0
        secureStorage.saveSilentPaymentLastScanHeight(walletId, currentHeight)
    }

    /**
     * Check if Silent Payments are initialized for wallet.
     *
     * @param walletId Wallet identifier
     * @return true if keys exist
     */
    fun isInitialized(walletId: String): Boolean {
        return secureStorage.getSilentPaymentScanKey(walletId) != null &&
            secureStorage.isSilentPaymentEnabled(walletId)
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
    fun getAddress(walletId: String): SilentPaymentAddress? {
        val scanKey = secureStorage.getSilentPaymentScanKey(walletId)
        val spendKey = secureStorage.getSilentPaymentSpendKey(walletId)

        if (scanKey == null || spendKey == null) {
            return null
        }

        // Derive public keys from private keys (STUB - needs secp256k1)
        // For now, we'll use the stored public keys if available
        // In real implementation: scanPublic = secp256k1_pubkey_create(scanPrivate)

        return try {
            SilentPaymentCrypto.createAddress(
                scanPublicKey = derivePublicKeyStub(scanKey),
                spendPublicKey = derivePublicKeyStub(spendKey),
                network = network,
            )
        } catch (e: Exception) {
            null
        }
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

        if (!secureStorage.isSilentPaymentEnabled(walletId)) {
            return emptyList()
        }

        val scanKey = secureStorage.getSilentPaymentScanKey(walletId)
        val spendKey = secureStorage.getSilentPaymentSpendKey(walletId)

        if (scanKey == null || spendKey == null) {
            return emptyList()
        }

        val fromHeight = secureStorage.getSilentPaymentLastScanHeight(walletId)
            .coerceAtLeast(config.activationHeight ?: 0)

        // Don't scan if already at tip
        if (fromHeight >= currentBlockHeight) {
            return emptyList()
        }

        // Derive scan public key from private key (STUB - needs secp256k1)
        val scanPublicKey = derivePublicKeyStub(scanKey)

        val request = SilentPaymentScanRequest(
            scanPublicKey = scanPublicKey,
            blockHeightFrom = fromHeight,
            blockHeightTo = currentBlockHeight,
        )

        return try {
            val response = scanApi.scan(request)

            // Update last scanned height
            secureStorage.saveSilentPaymentLastScanHeight(walletId, response.lastScannedHeight)

            // Save found outputs
            if (response.outputs.isNotEmpty()) {
                val existing = secureStorage.getSilentPaymentOutputs(walletId)
                val merged = (existing + response.outputs).distinctBy { "${it.txid}:${it.vout}" }
                secureStorage.saveSilentPaymentOutputs(walletId, merged)
            }

            response.outputs
        } catch (e: Exception) {
            // Log error but don't fail wallet sync
            emptyList()
        }
    }

    /**
     * Get all known Silent Payment outputs for a wallet.
     *
     * @param walletId Wallet identifier
     * @return List of saved Silent Payment outputs
     */
    fun getOutputs(walletId: String): List<SilentPaymentOutput> {
        return secureStorage.getSilentPaymentOutputs(walletId)
    }

    /**
     * Get spendable UTXOs with derived private keys.
     *
     * @param walletId Wallet identifier
     * @return List of spendable UTXOs
     */
    fun getSpendableUtxos(walletId: String): List<SilentPaymentSpendableUtxo> {
        val spendKey = secureStorage.getSilentPaymentSpendKey(walletId) ?: return emptyList()
        val outputs = secureStorage.getSilentPaymentOutputs(walletId).filter { !it.isSpent }

        return outputs.mapNotNull { output ->
            try {
                val tweakedPrivateKey = SilentPaymentCrypto.derivePrivateKey(
                    spendPrivateKey = spendKey,
                    tweak = output.tweak,
                )

                SilentPaymentSpendableUtxo(
                    txid = output.txid,
                    vout = output.vout,
                    amountSat = output.amountSat,
                    scriptPubKey = output.scriptPubKey,
                    privateKey = tweakedPrivateKey,
                    tweak = output.tweak,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get the total balance from Silent Payment outputs.
     *
     * @param walletId Wallet identifier
     * @return Balance in satoshis
     */
    fun getBalanceSat(walletId: String): Long {
        return secureStorage.getSilentPaymentOutputs(walletId)
            .filter { !it.isSpent }
            .sumOf { it.amountSat }
    }

    /**
     * Disable Silent Payments for wallet.
     *
     * @param walletId Wallet identifier
     */
    fun disable(walletId: String) {
        secureStorage.setSilentPaymentEnabled(walletId, false)
    }

    /**
     * Re-enable Silent Payments for wallet.
     *
     * @param walletId Wallet identifier
     */
    fun enable(walletId: String) {
        if (secureStorage.getSilentPaymentScanKey(walletId) != null) {
            secureStorage.setSilentPaymentEnabled(walletId, true)
        }
    }

    /**
     * Clear all Silent Payment data for wallet.
     *
     * WARNING: This deletes keys permanently! Only use when deleting wallet.
     *
     * @param walletId Wallet identifier
     */
    fun clear(walletId: String) {
        secureStorage.deleteSilentPaymentData(walletId)
    }

    // STUB: Derive public key from private key
    // This requires secp256k1 library - for now return dummy key
    private fun derivePublicKeyStub(privateKey: ByteArray): ByteArray {
        // STUB: Real implementation uses secp256k1_pubkey_create
        // Return 33-byte compressed public key placeholder
        return ByteArray(33) { if (it == 0) 0x02.toByte() else (it % 256).toByte() }
    }
}

/**
 * Factory for creating Silent Payment scan API instances.
 */
object SilentPaymentScanApiFactory {
    fun create(config: SilentPaymentConfig): SilentPaymentScanApi {
        return if (config.scanServerUrl != null) {
            SilentPaymentScanApiImpl(config.scanServerUrl)
        } else {
            SilentPaymentScanApiStub()
        }
    }
}

/**
 * Implementation of scan API that connects to a real server.
 * STUB - needs actual HTTP client implementation.
 */
class SilentPaymentScanApiImpl(private val serverUrl: String) : SilentPaymentScanApi {
    override suspend fun scan(request: SilentPaymentScanRequest): SilentPaymentScanResponse {
        // TODO: Implement real HTTP API call to scan server
        // This would POST the scanPublicKey to the server and receive matching outputs
        throw NotImplementedError("Real scan API not yet implemented")
    }
}
