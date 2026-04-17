package github.aeonbtc.ibiswallet.silentpayments

import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network

/**
 * Silent Payments (BIP 352) Models
 *
 * Silent Payments allow users to receive payments without revealing their addresses
 * on the blockchain. A static "Silent Payment address" is shared publicly, and
 * senders generate unique one-time addresses for each payment.
 *
 * Key concepts:
 * - Scan key: Used to scan blockchain for incoming payments (can be public)
 * - Spend key: Used to spend received funds (must remain private)
 * - Tweak: Adjusted public key created by combining scan key with sender's input public key
 */

/**
 * Silent Payment keys derived from wallet mnemonic.
 *
 * BIP352 uses hardened derivation paths:
 * - Scan key: m/352'/0'/0'/0' (testnet: m/352'/1'/0'/0')
 * - Spend key: m/352'/0'/0'/1' (testnet: m/352'/1'/0'/1')
 *
 * @property scanPrivateKey Private scan key for generating tweaked addresses
 * @property scanPublicKey Public scan key for server-assisted scanning
 * @property spendPrivateKey Private spend key for spending received funds
 * @property spendPublicKey Public spend key for address construction
 */
data class SilentPaymentKeys(
    val scanPrivateKey: ByteArray,
    val scanPublicKey: ByteArray,
    val spendPrivateKey: ByteArray,
    val spendPublicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SilentPaymentKeys) return false
        return scanPrivateKey.contentEquals(other.scanPrivateKey) &&
                scanPublicKey.contentEquals(other.scanPublicKey) &&
                spendPrivateKey.contentEquals(other.spendPrivateKey) &&
                spendPublicKey.contentEquals(other.spendPublicKey)
    }

    override fun hashCode(): Int {
        var result = scanPrivateKey.contentHashCode()
        result = 31 * result + scanPublicKey.contentHashCode()
        result = 31 * result + spendPrivateKey.contentHashCode()
        result = 31 * result + spendPublicKey.contentHashCode()
        return result
    }
}

/**
 * Silent Payment address (starts with "sp1" for mainnet, "tsp1" for testnet).
 *
 * Format: sp1q... (bech32m encoded scan and spend public keys)
 *
 * @property address The full Silent Payment address string
 * @property scanPublicKey Extracted scan public key
 * @property spendPublicKey Extracted spend public key
 * @property network Mainnet or Testnet
 * @property labels Optional labels for sub-addresses (BIP352 labels)
 */
data class SilentPaymentAddress(
    val address: String,
    val scanPublicKey: ByteArray,
    val spendPublicKey: ByteArray,
    val network: Network,
    val labels: List<Int> = emptyList(),
) {
    val isTestnet: Boolean get() = network == Network.TESTNET

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SilentPaymentAddress) return false
        return address == other.address &&
                scanPublicKey.contentEquals(other.scanPublicKey) &&
                spendPublicKey.contentEquals(other.spendPublicKey) &&
                network == other.network
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + scanPublicKey.contentHashCode()
        result = 31 * result + spendPublicKey.contentHashCode()
        result = 31 * result + network.hashCode()
        return result
    }
}

/**
 * Result of scanning a transaction for Silent Payment outputs.
 *
 * @property txid Transaction ID containing the payment
 * @property vout Output index
 * @property amountSat Amount in satoshis
 * @property tweak Tweak value used to derive the private key
 * @property scriptPubKey The P2TR output script
 * @property blockHeight Block height where transaction was mined
 * @property blockHash Block hash
 * @property timestamp When transaction was received
 */
data class SilentPaymentOutput(
    val txid: String,
    val vout: Int,
    val amountSat: Long,
    val tweak: ByteArray,
    val scriptPubKey: ByteArray,
    val blockHeight: Int,
    val blockHash: String,
    val timestamp: Long,
    val isSpent: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SilentPaymentOutput) return false
        return txid == other.txid &&
                vout == other.vout &&
                amountSat == other.amountSat &&
                tweak.contentEquals(other.tweak) &&
                scriptPubKey.contentEquals(other.scriptPubKey) &&
                blockHeight == other.blockHeight &&
                blockHash == other.blockHash &&
                timestamp == other.timestamp &&
                isSpent == other.isSpent
    }

    override fun hashCode(): Int {
        var result = txid.hashCode()
        result = 31 * result + vout
        result = 31 * result + amountSat.hashCode()
        result = 31 * result + tweak.contentHashCode()
        result = 31 * result + scriptPubKey.contentHashCode()
        result = 31 * result + blockHeight
        result = 31 * result + blockHash.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isSpent.hashCode()
        return result
    }
}

/**
 * Request for server-assisted Silent Payment scanning.
 *
 * @property scanPublicKey Public scan key (NOT private - safe to send to server)
 * @property blockHeightFrom Start scanning from this block
 * @property blockHeightTo Scan up to this block (inclusive)
 */
data class SilentPaymentScanRequest(
    val scanPublicKey: ByteArray,
    val blockHeightFrom: Int,
    val blockHeightTo: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SilentPaymentScanRequest) return false
        return scanPublicKey.contentEquals(other.scanPublicKey) &&
                blockHeightFrom == other.blockHeightFrom &&
                blockHeightTo == other.blockHeightTo
    }

    override fun hashCode(): Int {
        var result = scanPublicKey.contentHashCode()
        result = 31 * result + blockHeightFrom
        result = 31 * result + blockHeightTo
        return result
    }
}

/**
 * Response from server-assisted scanning.
 *
 * @property outputs List of found Silent Payment outputs
 * @property scannedBlocks Number of blocks scanned
 * @property lastScannedHeight Last block height that was processed
 */
data class SilentPaymentScanResponse(
    val outputs: List<SilentPaymentOutput>,
    val scannedBlocks: Int,
    val lastScannedHeight: Int,
)

/**
 * Configuration for Silent Payments feature.
 *
 * @property enabled Whether Silent Payments is enabled for this wallet
 * @property scanServerUrl URL of the server used for scanning (null = local scanning)
 * @property activationHeight Block height when SP was activated (for scanning)
 * @property useTestnetServer Whether to use testnet-specific scanning server
 */
data class SilentPaymentConfig(
    val enabled: Boolean = false,
    val scanServerUrl: String? = null,
    val activationHeight: Int? = null,
    val useTestnetServer: Boolean = true, // Default to testnet for development
)

/**
 * Exception types for Silent Payments operations.
 */
sealed class SilentPaymentException(message: String) : Exception(message) {
    class InvalidAddress(message: String) : SilentPaymentException(message)
    class CryptoError(message: String) : SilentPaymentException(message)
    class ScanError(message: String) : SilentPaymentException(message)
    class NetworkError(message: String) : SilentPaymentException(message)
}
