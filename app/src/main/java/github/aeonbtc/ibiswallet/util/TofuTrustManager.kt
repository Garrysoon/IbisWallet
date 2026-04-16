package github.aeonbtc.ibiswallet.util

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Certificate info presented by a server during SSL handshake.
 * Used for TOFU approval and mismatch dialogs.
 */
data class CertificateInfo(
    val host: String,
    val port: Int,
    val sha256Fingerprint: String,
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validUntil: String,
)

/**
 * Exception thrown when a server's certificate doesn't match the stored TOFU fingerprint.
 */
class CertificateMismatchException(
    val host: String,
    val port: Int,
    val storedFingerprint: String,
    val certInfo: CertificateInfo,
) : CertificateException("Certificate fingerprint mismatch for $host:$port")

/**
 * Exception thrown when connecting to a server for the first time (no stored fingerprint).
 * The caller should show the cert to the user for explicit approval.
 */
class CertificateFirstUseException(
    val certInfo: CertificateInfo,
) : CertificateException("First connection to ${certInfo.host}:${certInfo.port} - certificate approval required")

/**
 * TOFU (Trust-On-First-Use) X509TrustManager for Electrum server connections.
 *
 * Behavior:
 * - .onion hosts: trust all (Tor provides transport authentication)
 * - No stored fingerprint: throw CertificateFirstUseException (caller shows approval dialog)
 * - Stored fingerprint matches: accept silently
 * - Stored fingerprint mismatch: throw CertificateMismatchException (caller shows warning)
 *
 * The caller is responsible for:
 * 1. Catching CertificateFirstUseException and showing the cert for approval
 * 2. Catching CertificateMismatchException and showing a warning
 * 3. Storing the approved fingerprint via SecureStorage
 */
/**
 * SECURITY NOTE: This is a custom X509TrustManager implementing TOFU (Trust-On-First-Use).
 * This is INTENTIONAL and SECURE because:
 * 1. We don't trust all certificates - we only trust the FIRST certificate seen for a server
 * 2. Once a certificate is approved by the user, it's stored and must match for all future connections
 * 3. .onion addresses bypass SSL validation because Tor provides transport-level authentication
 * 4. Certificate changes trigger explicit user warnings (CertificateMismatchException)
 * 
 * This is the same approach used by SSH and other security-conscious applications.
 */
class TofuTrustManager(
    private val host: String,
    private val port: Int,
    private val storedFingerprint: String?,
    private val isOnionHost: Boolean = host.endsWith(".onion"),
) : X509TrustManager {
    /** Full certificate info from the last handshake */
    var presentedCertInfo: CertificateInfo? = null
        private set

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        // Client authentication is not used - this is a client-only implementation.
        // If called, we reject all client certificates as we don't expect any.
        throw CertificateException("Client certificate authentication is not supported")
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        if (chain.isEmpty()) throw CertificateException("Empty certificate chain")

        val cert = chain[0]
        val fingerprint = computeFingerprint(cert)

        val certInfo =
            CertificateInfo(
                host = host,
                port = port,
                sha256Fingerprint = fingerprint,
                subject = cert.subjectX500Principal?.name ?: "Unknown",
                issuer = cert.issuerX500Principal?.name ?: "Unknown",
                validFrom = cert.notBefore?.toString() ?: "Unknown",
                validUntil = cert.notAfter?.toString() ?: "Unknown",
            )
        presentedCertInfo = certInfo

        // .onion addresses: Tor provides transport-level authentication
        // The .onion address itself is a public key commitment
        if (isOnionHost) return

        when {
            storedFingerprint == null -> {
                // First connection - require explicit user approval
                throw CertificateFirstUseException(certInfo)
            }
            storedFingerprint != fingerprint -> {
                // Certificate changed - possible MITM
                throw CertificateMismatchException(
                    host = host,
                    port = port,
                    storedFingerprint = storedFingerprint,
                    certInfo = certInfo,
                )
            }
            // Fingerprint matches - connection is trusted
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    companion object {
        /**
         * Compute SHA-256 fingerprint of a certificate's DER encoding.
         * Returns colon-separated hex string: "AB:CD:EF:..."
         */
        fun computeFingerprint(cert: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(cert.encoded)
            return hash.joinToString(":") { "%02X".format(it) }
        }

        /**
         * Create an SSLSocketFactory using a TofuTrustManager.
         */
        fun createSSLSocketFactory(trustManager: TofuTrustManager): SSLSocketFactory {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
            return sslContext.socketFactory
        }
    }
}
