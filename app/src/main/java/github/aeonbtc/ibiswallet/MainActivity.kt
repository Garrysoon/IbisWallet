package github.aeonbtc.ibiswallet

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.IbisWalletApp
import github.aeonbtc.ibiswallet.ui.screens.CalculatorScreen
import github.aeonbtc.ibiswallet.ui.screens.LockScreen
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.IbisWalletTheme
import github.aeonbtc.ibiswallet.viewmodel.WalletViewModel
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class MainActivity : FragmentActivity() {
    private lateinit var secureStorage: SecureStorage
    private lateinit var walletViewModel: WalletViewModel
    private var isUnlocked by mutableStateOf(false)
    private var cloakBypassed by mutableStateOf(false)
    private var biometricPrompt: BiometricPrompt? = null
    private var wasInBackground = false
    private val biometricAutoCancelHandler = Handler(Looper.getMainLooper())
    private var isCloakActive = false

    // NFC foreground dispatch — enabled when Send screen is active so NFC tag
    // reads go directly to this activity instead of triggering the system chooser.
    private var nfcAdapter: NfcAdapter? = null
    private var nfcForegroundRequested = false
    private var nfcForegroundActive = false

    /**
     * Enable NFC foreground dispatch so this activity receives NFC tag reads
     * with priority over the manifest intent filter. Called when the Send screen
     * becomes visible and NFC is enabled in settings.
     */
    fun enableNfcForegroundDispatch() {
        nfcForegroundRequested = true
        activateNfcForegroundDispatch()
    }

    /**
     * Disable NFC foreground dispatch. Called when the Send screen is no longer visible.
     */
    fun disableNfcForegroundDispatch() {
        nfcForegroundRequested = false
        deactivateNfcForegroundDispatch()
    }

    private fun activateNfcForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        if (nfcForegroundActive) return
        if (!secureStorage.isNfcEnabled()) return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Catch all NDEF tags, plus tech-discovered for non-NDEF NFC devices (HCE peers)
        val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try { addDataScheme("bitcoin") } catch (_: Exception) { }
        }
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val filters = arrayOf(ndefFilter, techFilter, tagFilter)
        val techLists = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(IsoDep::class.java.name),
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name),
        )
        try {
            adapter.enableForegroundDispatch(this, pendingIntent, filters, techLists)
            nfcForegroundActive = true
        } catch (_: Exception) { }
    }

    private fun deactivateNfcForegroundDispatch() {
        if (!nfcForegroundActive) return
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (_: Exception) { }
        nfcForegroundActive = false
    }

    /**
     * Apply any pending launcher icon alias swap. Called early in onCreate
     * before any UI to avoid the Android 10+ process-kill race.
     */
    private fun applyPendingIconSwap() {
        val pending = secureStorage.getPendingIconAlias() ?: return
        val current = secureStorage.getCurrentIconAlias()
        if (pending == current) {
            secureStorage.clearPendingIconAlias()
            return
        }

        val pm = packageManager
        val pkg = packageName
        val allAliases = listOf(SecureStorage.ALIAS_DEFAULT, SecureStorage.ALIAS_CALCULATOR)

        for (alias in allAliases) {
            val state =
                if (alias == pending) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg$alias"),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }

        secureStorage.setCurrentIconAlias(pending)
        secureStorage.clearPendingIconAlias()
    }

    /**
     * Disguise the app in the recent apps / task switcher when cloak mode is active.
     */
    @Suppress("DEPRECATION")
    private fun updateTaskDescription() {
        if (isCloakActive) {
            setTaskDescription(
                ActivityManager.TaskDescription(
                    getString(R.string.cloak_calculator_label),
                ),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prevent tapjacking/overlay attacks
        window.decorView.filterTouchesWhenObscured = true

        secureStorage = SecureStorage(this)
        walletViewModel = ViewModelProvider(this)[WalletViewModel::class.java]
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Apply pending icon swap before any UI
        applyPendingIconSwap()

        // Check cloak mode
        isCloakActive = secureStorage.isCloakModeEnabled() && secureStorage.getCloakCode() != null
        updateTaskDescription()

        // Apply screenshot prevention if enabled
        if (secureStorage.getDisableScreenshots()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        // Check if security is enabled - always locked on fresh start
        // When cloak mode is active, stay locked so the calculator screen shows first
        val securityMethod = secureStorage.getSecurityMethod()
        isUnlocked = securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive

        // Setup biometric prompt
        setupBiometricPrompt()

        // Check for incoming bitcoin: URI intent
        handleBitcoinIntent(intent)

        setContent {
            IbisWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground,
                ) {
                    if (isUnlocked) {
                        IbisWalletApp(
                            onLockApp = { isUnlocked = false },
                        )
                    } else if (isCloakActive && !cloakBypassed) {
                        // Show calculator disguise — entering the secret code bypasses it
                        CalculatorScreen(
                            cloakCode = secureStorage.getCloakCode() ?: "",
                            onUnlock = {
                                cloakBypassed = true
                                val secMethod = secureStorage.getSecurityMethod()
                                if (secMethod == SecureStorage.SecurityMethod.NONE) {
                                    // No additional auth — go straight to wallet
                                    isUnlocked = true
                                }
                                // Otherwise fall through to LockScreen on next recompose
                            },
                        )
                    } else {
                        val biometricManager = BiometricManager.from(this)
                        val isBiometricAvailable =
                            biometricManager.canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG,
                            ) == BiometricManager.BIOMETRIC_SUCCESS

                        val secMethod = secureStorage.getSecurityMethod()
                        val isDuressEnabled = secureStorage.isDuressEnabled()
                        val isDuressWithBiometric =
                            isDuressEnabled &&
                                secMethod == SecureStorage.SecurityMethod.BIOMETRIC

                        LockScreen(
                            securityMethod = secMethod,
                            onPinEntered = { pin ->
                                // For PIN mode: check real PIN first, then duress PIN
                                // For BIOMETRIC mode with duress: only duress PIN works
                                //   (real wallet accessed via biometric through the C button)
                                val isRealPin =
                                    secMethod == SecureStorage.SecurityMethod.PIN &&
                                        secureStorage.verifyPin(pin)
                                // When verifyPin already ran and failed, it incremented
                                // the shared counter — don't double-count in verifyDuressPin
                                val realPinWasTried = secMethod == SecureStorage.SecurityMethod.PIN
                                val isDuressPin =
                                    !isRealPin && isDuressEnabled &&
                                        secureStorage.verifyDuressPin(pin, incrementFailedAttempts = !realPinWasTried)

                                when {
                                    isRealPin -> {
                                        walletViewModel.exitDuressMode()
                                        isUnlocked = true
                                        true
                                    }
                                    isDuressPin -> {
                                        walletViewModel.enterDuressMode()
                                        isUnlocked = true
                                        true
                                    }
                                    else -> {
                                        // Check if failed attempts reached auto-wipe threshold
                                        if (secureStorage.shouldAutoWipe()) {
                                            walletViewModel.wipeAllData {
                                                // Kill the process to simulate a crash — no restart,
                                                // no fresh state visible. The app simply vanishes.
                                                android.os.Process.killProcess(android.os.Process.myPid())
                                            }
                                        }
                                        false
                                    }
                                }
                            },
                            onBiometricRequest = {
                                showBiometricPrompt(isDuressWithBiometric)
                            },
                            isBiometricAvailable = isBiometricAvailable,
                            storedPinLength =
                                if (isDuressWithBiometric) {
                                    secureStorage.getDuressPinLength()
                                } else {
                                    secureStorage.getStoredPinLength()
                                },
                            isDuressWithBiometric = isDuressWithBiometric,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Re-enable NFC foreground dispatch if the Send screen requested it
        // (Android requires disabling in onPause and re-enabling in onResume)
        if (nfcForegroundRequested) {
            activateNfcForegroundDispatch()
        }

        val securityMethod = secureStorage.getSecurityMethod()
        if (securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive) {
            isUnlocked = true
            return
        }
        // Cloak mode with no PIN/biometric: nothing to re-lock via timing
        if (securityMethod == SecureStorage.SecurityMethod.NONE) {
            return
        }

        // Check if we need to lock based on timing settings
        if (wasInBackground) {
            wasInBackground = false
            when (val lockTiming = secureStorage.getLockTiming()) {
                SecureStorage.LockTiming.DISABLED -> {
                    // Never auto-lock after initial unlock
                }
                SecureStorage.LockTiming.WHEN_MINIMIZED -> {
                    // Already locked in onStop
                }
                else -> {
                    // Check if timeout has elapsed
                    val lastBackgroundTime = secureStorage.getLastBackgroundTime()
                    val elapsedTime = System.currentTimeMillis() - lastBackgroundTime
                    if (elapsedTime >= lockTiming.timeoutMs) {
                        isUnlocked = false
                        if (isCloakActive) cloakBypassed = false
                    }
                }
            }
        }

        // Trigger biometric if locked and biometric is enabled.
        // Skip when cloak is active and not yet bypassed — calculator must be entered first.
        // Skip auto-trigger in duress+biometric mode (the C button is the hidden trigger).
        if (!isUnlocked &&
            securityMethod == SecureStorage.SecurityMethod.BIOMETRIC &&
            !secureStorage.isDuressEnabled() &&
            !(isCloakActive && !cloakBypassed)
        ) {
            showBiometricPrompt()
        }
    }

    override fun onPause() {
        super.onPause()
        deactivateNfcForegroundDispatch()
    }

    override fun onStop() {
        super.onStop()

        val securityMethod = secureStorage.getSecurityMethod()
        if (securityMethod == SecureStorage.SecurityMethod.NONE && !isCloakActive) {
            return
        }
        // Cloak mode with no PIN: re-engage calculator immediately on background
        if (securityMethod == SecureStorage.SecurityMethod.NONE && isCloakActive) {
            isUnlocked = false
            cloakBypassed = false
            return
        }

        wasInBackground = true
        val lockTiming = secureStorage.getLockTiming()

        when (lockTiming) {
            SecureStorage.LockTiming.DISABLED -> {
                // Never lock when going to background
            }
            SecureStorage.LockTiming.WHEN_MINIMIZED -> {
                // Lock immediately and re-engage cloak
                isUnlocked = false
                if (isCloakActive) cloakBypassed = false
            }
            else -> {
                // Record the time we went to background
                secureStorage.setLastBackgroundTime(System.currentTimeMillis())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleBitcoinIntent(intent)
    }

    /**
     * Extract a bitcoin: URI from the intent and store it in the ViewModel.
     * Handles regular VIEW intents (browser/app links) and NFC foreground dispatch
     * intents (only active on the Send screen).
     */
    private fun handleBitcoinIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Check intent data URI (covers ACTION_VIEW with bitcoin: scheme)
        intent.data?.let { data ->
            if (data.scheme?.lowercase() == "bitcoin") {
                walletViewModel.setPendingBitcoinUri(data.toString())
                return
            }
        }

        // 2. NFC intents — only process when foreground dispatch is active (Send screen)
        if (!nfcForegroundRequested) return
        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            val bitcoinUri = extractBitcoinFromNdef(intent)
            if (bitcoinUri != null) {
                walletViewModel.setPendingBitcoinUri(bitcoinUri)
            }
        }
    }

    /**
     * Extract a bitcoin URI or address from NDEF message payloads.
     * Handles NDEF Text records and URI records that may not have been auto-dispatched.
     */
    @Suppress("DEPRECATION")
    private fun extractBitcoinFromNdef(intent: Intent): String? {
        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            ?: return null

        for (raw in messages) {
            val message = raw as? NdefMessage ?: continue
            for (record in message.records) {
                val text = parseNdefRecord(record) ?: continue
                val trimmed = text.trim()
                // Accept bitcoin: URIs or bare addresses (common prefixes)
                when {
                    trimmed.lowercase().startsWith("bitcoin:") -> return trimmed
                    trimmed.startsWith("bc1") -> return "bitcoin:$trimmed"
                    trimmed.startsWith("1") && trimmed.length in 25..34 -> return "bitcoin:$trimmed"
                    trimmed.startsWith("3") && trimmed.length in 25..34 -> return "bitcoin:$trimmed"
                }
            }
        }
        return null
    }

    /**
     * Parse an NDEF record into a string, handling both URI and Text record types.
     */
    private fun parseNdefRecord(record: NdefRecord): String? {
        return when (record.tnf) {
            NdefRecord.TNF_WELL_KNOWN -> {
                when {
                    record.type.contentEquals(NdefRecord.RTD_URI) -> {
                        // URI record: first byte is prefix code, rest is URI
                        record.toUri()?.toString()
                    }
                    record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                        // Text record: first byte = status (bit 7 = encoding, bits 5-0 = lang length)
                        val payload = record.payload
                        if (payload.isEmpty()) return null
                        val status = payload[0].toInt() and 0xFF
                        val langLength = status and 0x3F
                        if (payload.size <= 1 + langLength) return null
                        val encoding = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
                        String(payload, 1 + langLength, payload.size - 1 - langLength, encoding)
                    }
                    else -> null
                }
            }
            NdefRecord.TNF_ABSOLUTE_URI -> {
                String(record.payload, Charsets.UTF_8)
            }
            else -> null
        }
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt =
            BiometricPrompt(
                this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        biometricAutoCancelHandler.removeCallbacksAndMessages(null)
                        // Biometric always opens the real wallet
                        walletViewModel.exitDuressMode()
                        isUnlocked = true
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        biometricAutoCancelHandler.removeCallbacksAndMessages(null)
                        // User can still use PIN as fallback
                    }

                    // onAuthenticationFailed not overridden — biometric failures
                    // do not count toward auto-wipe; the OS already rate-limits
                    // biometric attempts and falls back to PIN entry.
                },
            )
    }

    private fun showBiometricPrompt(autoCancelAfter2s: Boolean = false) {
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (isCloakActive) "Authenticate" else "Unlock Ibis Wallet")
                .setSubtitle(if (isCloakActive) "Verify your identity" else "Authenticate to access your wallet")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

        // Tie authentication to a KeyStore cryptographic operation so it cannot
        // be bypassed by injecting a success callback on rooted/instrumented devices.
        val cryptoObject =
            try {
                val cipher = getBiometricCipher()
                BiometricPrompt.CryptoObject(cipher)
            } catch (_: Exception) {
                // KeyStore unavailable — fall back to prompt-only auth
                null
            }

        if (cryptoObject != null) {
            biometricPrompt?.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt?.authenticate(promptInfo)
        }

        // In duress+biometric mode, auto-cancel the biometric prompt after 2 seconds
        // so an attacker who accidentally hits C sees it disappear quickly
        if (autoCancelAfter2s) {
            biometricAutoCancelHandler.removeCallbacksAndMessages(null)
            biometricAutoCancelHandler.postDelayed({
                biometricPrompt?.cancelAuthentication()
            }, 2000L)
        }
    }

    /**
     * Get or create a KeyStore-backed AES key that requires biometric auth,
     * and return an initialized Cipher for CryptoObject binding.
     */
    private fun getBiometricCipher(): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyAlias = "ibis_biometric_key"

        if (!keyStore.containsAlias(keyAlias)) {
            val keyGen =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore",
                )
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build(),
            )
            keyGen.generateKey()
        }

        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
        return Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}",
        ).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
    }
}
