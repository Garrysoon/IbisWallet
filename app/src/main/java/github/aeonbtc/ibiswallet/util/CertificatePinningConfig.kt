package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.BuildConfig
import okhttp3.CertificatePinner

/**
 * Certificate pinning configuration for critical remote services.
 * 
 * SECURITY: This prevents MITM attacks by validating that the server's
 * certificate matches an expected SHA256 hash (pin). If the certificate
 * doesn't match, the connection is rejected.
 * 
 * TODO: Add actual SHA256 certificate hashes for production:
 * 1. Get certificate fingerprint: openssl s_client -connect api.boltz.exchange:443 < /dev/null 2>/dev/null | openssl x509 -in /dev/stdin -noout -sha256 -fingerprint
 * 2. Convert format: sha256/AAAAAAAAAAAAAAAAAAAAA= 
 * 3. Add backup pins (intermediate/leaf certificates) for certificate rotation
 */
object CertificatePinningConfig {

    /**
     * Creates a CertificatePinner for Boltz API connections.
     * 
     * TODO: Replace with actual certificate pins before production deployment.
     * Example format:
     * .add("api.boltz.exchange", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
     * .add("api.boltz.exchange", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // backup pin
     */
    fun createBoltzPinner(): CertificatePinner {
        return if (BuildConfig.DEBUG) {
            // Debug builds: no pinning (allows testing with different certificates)
            CertificatePinner.Builder().build()
        } else {
            // Release builds: require certificate pinning
            CertificatePinner.Builder()
                // TODO: Add production certificate pins here
                // .add("api.boltz.exchange", "sha256/PLACEHOLDER_GET_FROM_CERTIFICATE=")
                // .add("api.boltz.exchange", "sha256/PLACEHOLDER_BACKUP_PIN=")
                .build()
        }
    }

    /**
     * Creates a CertificatePinner for SideSwap API connections.
     * 
     * TODO: Replace with actual certificate pins before production deployment.
     */
    fun createSideSwapPinner(): CertificatePinner {
        return if (BuildConfig.DEBUG) {
            CertificatePinner.Builder().build()
        } else {
            CertificatePinner.Builder()
                // TODO: Add production certificate pins here
                // .add("api.sideswap.io", "sha256/PLACEHOLDER_GET_FROM_CERTIFICATE=")
                .build()
        }
    }

    /**
     * Creates a CertificatePinner for mempool.space and block explorer APIs.
     */
    fun createMempoolPinner(): CertificatePinner {
        return if (BuildConfig.DEBUG) {
            CertificatePinner.Builder().build()
        } else {
            CertificatePinner.Builder()
                // TODO: Add production certificate pins here
                // .add("mempool.space", "sha256/PLACEHOLDER_GET_FROM_CERTIFICATE=")
                // .add("blockstream.info", "sha256/PLACEHOLDER_GET_FROM_CERTIFICATE=")
                .build()
        }
    }

    /**
     * Creates a CertificatePinner for Electrum server connections.
     * Note: Most Electrum servers use self-signed certificates, so TOFU
     * (Trust-On-First-Use) is used instead of pinning. See TofuTrustManager.
     */
    fun createElectrumPinner(): CertificatePinner {
        // Electrum servers typically use self-signed certificates with TOFU
        // Pinning is not practical here as users can configure custom servers
        return CertificatePinner.Builder().build()
    }
}
