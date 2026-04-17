package github.aeonbtc.ibiswallet.silentpayments

/**
 * Stub API for Silent Payment server-assisted scanning.
 *
 * This is a placeholder implementation that returns empty results.
 * In production, replace with real server API (Esplora, custom server, etc.)
 *
 * The real implementation would make HTTP requests to a scanning server:
 * POST /silent-payments/scan
 * Body: { scanPublicKey: hex, fromHeight: int, toHeight: int }
 * Response: { outputs: [...], lastHeight: int }
 */
class SilentPaymentScanApiStub : SilentPaymentScanApi {

    override suspend fun scan(request: SilentPaymentScanRequest): SilentPaymentScanResponse {
        // STUB: Always returns empty result
        // In production, this would:
        // 1. POST scanPublicKey to server
        // 2. Server scans all UTXOs for tweaks matching the key
        // 3. Returns found outputs with tweak data

        return SilentPaymentScanResponse(
            outputs = emptyList(),
            scannedBlocks = 0,
            lastScannedHeight = request.blockHeightTo,
        )
    }

    /**
     * Check if this API is actually a stub.
     */
    fun isStub(): Boolean = true
}

/**
 * Interface for Silent Payment scanning API.
 *
 * Implementations:
 * - SilentPaymentScanApiStub: Empty stub for development
 * - SilentPaymentScanApiEsplora: Blockstream/Esplora API (todo)
 * - SilentPaymentScanApiCustom: Your own scanning server (todo)
 */
interface SilentPaymentScanApi {
    /**
     * Scan for Silent Payment outputs.
     *
     * @param request Scan request containing public scan key and block range
     * @return Response with found outputs and scan metadata
     */
    suspend fun scan(request: SilentPaymentScanRequest): SilentPaymentScanResponse
}

/**
 * Factory to create appropriate scan API based on configuration.
 */
object SilentPaymentScanApiFactory {
    /**
     * Create scan API instance.
     *
     * @param config Silent Payment configuration
     * @return SilentPaymentScanApi implementation
     */
    fun create(config: SilentPaymentConfig): SilentPaymentScanApi {
        return when {
            config.scanServerUrl == null -> SilentPaymentScanApiStub()
            config.scanServerUrl.contains("blockstream") -> {
                // TODO: Implement Esplora API
                SilentPaymentScanApiStub()
            }
            else -> {
                // TODO: Implement custom server API
                SilentPaymentScanApiStub()
            }
        }
    }
}
