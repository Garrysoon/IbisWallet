package github.aeonbtc.ibiswallet.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.BtcPriceService
import github.aeonbtc.ibiswallet.data.FeeEstimationService
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.DryRunResult
import github.aeonbtc.ibiswallet.data.model.ElectrumConfig
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletResult
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.data.repository.WalletRepository
import github.aeonbtc.ibiswallet.tor.TorManager
import github.aeonbtc.ibiswallet.tor.TorState
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.util.Bip329LabelNetwork
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.Bip329Labels
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.CertificateFirstUseException
import github.aeonbtc.ibiswallet.util.CertificateInfo
import github.aeonbtc.ibiswallet.util.CertificateMismatchException
import github.aeonbtc.ibiswallet.util.CryptoUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for managing wallet state across the app
 */
class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WalletRepository(application)
    private val secureStorage = SecureStorage.getInstance(application)
    private val torManager = TorManager.getInstance(application)
    private val feeEstimationService = FeeEstimationService()
    private val btcPriceService = BtcPriceService()

    val walletState: StateFlow<WalletState> = repository.walletState
    val torState: StateFlow<TorState> = torManager.torState

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WalletEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<WalletEvent> = _events.asSharedFlow()

    private val _fullBackupResultMessage = MutableStateFlow<String?>(null)
    val fullBackupResultMessage: StateFlow<String?> = _fullBackupResultMessage.asStateFlow()

    private val _settingsRefreshVersion = MutableStateFlow(0L)
    val settingsRefreshVersion: StateFlow<Long> = _settingsRefreshVersion.asStateFlow()

    fun clearFullBackupResult() {
        _fullBackupResultMessage.value = null
    }

    fun setFullBackupResult(message: String) {
        _fullBackupResultMessage.value = message
    }

    // Active connection job (cancellable)
    private var connectionJob: Job? = null

    // Background sync loop job - runs every ~5min while connected
    private var backgroundSyncJob: Job? = null

    // Heartbeat loop job - pings server every ~60s to detect dead connections fast
    private var heartbeatJob: Job? = null

    private var fullSyncJob: Job? = null
    @Volatile private var fullSyncCancelRequested = false

    private var addressRefreshJob: Job? = null
    private var labelRefreshJob: Job? = null
    private var syncTimesRefreshJob: Job? = null

    // Server state for reactive UI updates
    private val _serversState = MutableStateFlow(ServersState())
    val serversState: StateFlow<ServersState> = _serversState.asStateFlow()

    // Layer 1 denomination state (BTC or SATS)
    private val _denominationState = MutableStateFlow(repository.getLayer1Denomination())
    val denominationState: StateFlow<String> = _denominationState.asStateFlow()

    // Mempool server state
    private val _mempoolServerState = MutableStateFlow(repository.getMempoolServer())
    val mempoolServerState: StateFlow<String> = _mempoolServerState.asStateFlow()

    // Fee estimation state
    private val _feeSourceState = MutableStateFlow(repository.getFeeSource())
    val feeSourceState: StateFlow<String> = _feeSourceState.asStateFlow()

    private val _feeEstimationState = MutableStateFlow<FeeEstimationResult>(FeeEstimationResult.Disabled)
    val feeEstimationState: StateFlow<FeeEstimationResult> = _feeEstimationState.asStateFlow()

    // BTC/USD price source state
    private val _priceSourceState = MutableStateFlow(repository.getPriceSource())
    val priceSourceState: StateFlow<String> = _priceSourceState.asStateFlow()

    // BTC/USD price state (null if disabled or fetch failed)
    private val _btcPriceState = MutableStateFlow<Double?>(null)
    val btcPriceState: StateFlow<Double?> = _btcPriceState.asStateFlow()

    // Minimum fee rate from connected Electrum server
    val minFeeRate: StateFlow<Double> = repository.minFeeRate

    // Privacy mode - hides all amounts when active (persisted across sessions)
    private val _privacyMode = MutableStateFlow(repository.getPrivacyMode())
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    // Swipe navigation mode
    private val _swipeMode = MutableStateFlow(repository.getSwipeMode())
    val swipeMode: StateFlow<String> = _swipeMode.asStateFlow()

    // Pre-selected UTXO for coin control (set from AllUtxosScreen)
    private val _preSelectedUtxo = MutableStateFlow<UtxoInfo?>(null)
    val preSelectedUtxo: StateFlow<UtxoInfo?> = _preSelectedUtxo.asStateFlow()

    // All UTXOs — populated asynchronously on IO to avoid BDK JNI calls on the main thread.
    // Refreshed when balance or active wallet changes, and after freeze/unfreeze operations.
    private val _allUtxos = MutableStateFlow<List<UtxoInfo>>(emptyList())
    val allUtxos: StateFlow<List<UtxoInfo>> = _allUtxos.asStateFlow()

    private val _allAddresses =
        MutableStateFlow<Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>>>(
            Triple(emptyList(), emptyList(), emptyList()),
        )
    val allAddresses: StateFlow<Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>>> =
        _allAddresses.asStateFlow()

    private val _allWalletAddresses = MutableStateFlow<Set<String>>(emptySet())
    val allWalletAddresses: StateFlow<Set<String>> = _allWalletAddresses.asStateFlow()

    private val _addressLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val addressLabels: StateFlow<Map<String, String>> = _addressLabels.asStateFlow()

    private val _transactionLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val transactionLabels: StateFlow<Map<String, String>> = _transactionLabels.asStateFlow()

    private val _walletLastFullSyncTimes = MutableStateFlow<Map<String, Long?>>(emptyMap())
    val walletLastFullSyncTimes: StateFlow<Map<String, Long?>> = _walletLastFullSyncTimes.asStateFlow()

    // Track which wallet is currently being synced (for UI progress indicator)
    private val _syncingWalletId = MutableStateFlow<String?>(null)
    val syncingWalletId: StateFlow<String?> = _syncingWalletId.asStateFlow()

    // Send screen draft state (persisted while app is open/minimized)
    private val _sendScreenDraft = MutableStateFlow(SendScreenDraft())
    val sendScreenDraft: StateFlow<SendScreenDraft> = _sendScreenDraft.asStateFlow()

    // Pending send input from external intent or NFC (consumed by IbisWalletApp after unlock)
    private val _pendingSendInput = MutableStateFlow<String?>(null)
    val pendingSendInput: StateFlow<String?> = _pendingSendInput.asStateFlow()

    // PSBT state for watch-only wallet signing flow
    private val _psbtState = MutableStateFlow(PsbtState())
    val psbtState: StateFlow<PsbtState> = _psbtState.asStateFlow()

    // Certificate TOFU state
    private val _certDialogState = MutableStateFlow<CertDialogState?>(null)
    val certDialogState: StateFlow<CertDialogState?> = _certDialogState.asStateFlow()

    // Dry-run fee estimation from BDK TxBuilder
    private val _dryRunResult = MutableStateFlow<DryRunResult?>(null)
    val dryRunResult: StateFlow<DryRunResult?> = _dryRunResult.asStateFlow()
    private var dryRunJob: Job? = null

    // Manual broadcast state (standalone tx broadcast, not tied to any wallet)
    private val _manualBroadcastState = MutableStateFlow(ManualBroadcastState())
    val manualBroadcastState: StateFlow<ManualBroadcastState> = _manualBroadcastState.asStateFlow()

    // Auto-switch server on disconnect
    private val _autoSwitchServer = MutableStateFlow(repository.isAutoSwitchServerEnabled())
    val autoSwitchServer: StateFlow<Boolean> = _autoSwitchServer.asStateFlow()

    // Duress mode state (not persisted — resets on app restart which forces re-auth via lock screen)
    private val _isDuressMode = MutableStateFlow(false)
    val isDuressMode: StateFlow<Boolean> = _isDuressMode.asStateFlow()

    // Guards notification firing until the first post-connection sync completes,
    // preventing stale transactions from being treated as new on cold start.
    private val _initialSyncComplete = MutableStateFlow(false)
    val initialSyncComplete: StateFlow<Boolean> = _initialSyncComplete.asStateFlow()

    private val lifecycleCoordinator =
        AppLifecycleCoordinator(
            scope = viewModelScope,
            onBackgrounded = {
                _uiState.value.isConnected
            },
            onForegrounded = { wasConnectedBeforeBackground, _ ->
                if (wasConnectedBeforeBackground && !_uiState.value.isConnected) {
                    reconnectOnForeground()
                }
            },
        )

    init {
        // Initialize servers state
        refreshServersState()

        fetchFeeEstimates()

        // Load existing wallet and auto-connect to Electrum on startup
        viewModelScope.launch {
            // Load wallet if one exists
            if (repository.isWalletInitialized()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
                when (val result = repository.loadWallet()) {
                    is WalletResult.Success -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    is WalletResult.Error -> {
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                error = result.message,
                            )
                    }
                }
            }

            // Auto-connect to saved Electrum server if available,
            // unless the user explicitly disconnected last session.
            if (!repository.isUserDisconnected()) {
                repository.getElectrumConfig()?.let { config ->
                    connectToElectrum(config)
                }
            } else {
                // No auto-connect — mark initial sync as complete so
                // persistent notified-txid tracking works for offline mode.
                _initialSyncComplete.value = true
            }
        }

        // Refresh UTXOs asynchronously when wallet sync state changes in a way that can
        // affect confirmation status, balance, or the active wallet.
        // Uses map + distinctUntilChanged to avoid re-fetching on irrelevant
        // walletState emissions (sync progress, block height, error, etc.).
        viewModelScope.launch(Dispatchers.IO) {
            walletState
                .map { state ->
                    state.isInitialized to Triple(
                        state.balanceSats,
                        state.activeWallet?.id,
                        state.lastSyncTimestamp,
                    )
                }
                .distinctUntilChanged()
                .collect { (initialized, _) ->
                    if (initialized) {
                        _allUtxos.value = repository.getAllUtxos()
                        refreshAddressBook()
                        refreshLabelSnapshots()
                    } else {
                        clearDerivedWalletSnapshots()
                    }
                }
        }

        viewModelScope.launch {
            walletState
                .map { state -> state.wallets.map { it.id } to state.activeWallet?.id }
                .distinctUntilChanged()
                .collect {
                    refreshWalletLastFullSyncTimes()
                }
        }
    }

    private fun clearDerivedWalletSnapshots() {
        _allAddresses.value = Triple(emptyList(), emptyList(), emptyList())
        _allWalletAddresses.value = emptySet()
        _addressLabels.value = emptyMap()
        _transactionLabels.value = emptyMap()
        _walletLastFullSyncTimes.value = emptyMap()
    }

    private fun refreshAddressBook() {
        addressRefreshJob?.cancel()
        addressRefreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                val addresses = repository.getAllAddresses()
                _allAddresses.value = addresses
                _allWalletAddresses.value =
                    buildSet {
                        addresses.first.forEach { add(it.address) }
                        addresses.second.forEach { add(it.address) }
                        addresses.third.forEach { add(it.address) }
                    }
            }
    }

    private fun refreshLabelSnapshots() {
        labelRefreshJob?.cancel()
        labelRefreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                _addressLabels.value = repository.getAllAddressLabels()
                _transactionLabels.value = repository.getAllTransactionLabels()
            }
    }

    private fun refreshWalletLastFullSyncTimes() {
        syncTimesRefreshJob?.cancel()
        syncTimesRefreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                val walletIds = walletState.value.wallets.map { it.id }
                _walletLastFullSyncTimes.value =
                    walletIds.associateWith { walletId ->
                        repository.getLastFullSyncTime(walletId)
                    }
            }
    }

    private fun refreshCurrentWalletSnapshots() {
        refreshAddressBook()
        refreshLabelSnapshots()
        refreshUtxos()
    }

    private fun refreshActiveWalletSnapshotsIfNeeded(walletId: String) {
        if (walletId == repository.getActiveWalletId()) {
            refreshCurrentWalletSnapshots()
        }
    }

    /**
     * Silent reconnect after returning from background with a dead connection.
     * Shows "Connecting" status in UI.
     */
    private suspend fun reconnectOnForeground() {
        if (repository.isUserDisconnected()) return
        if (connectionJob?.isActive == true) return
        val config = repository.getElectrumConfig() ?: return

        _uiState.value = _uiState.value.copy(isConnecting = true, isConnected = false)

        val needsTor = config.isOnionAddress()

        if (needsTor && !torManager.isReady()) {
            torManager.start()
            if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false)
                _events.emit(WalletEvent.Error("Connection lost"))
                return
            }
            delay(TOR_POST_BOOTSTRAP_DELAY_MS)
        }

        val timeoutMs = if (needsTor) CONNECTION_TIMEOUT_TOR_MS else CONNECTION_TIMEOUT_CLEARNET_MS
        val result =
            withTimeoutOrNull(timeoutMs) {
                repository.connectToElectrum(config)
            }

        when (result) {
            is WalletResult.Success<*> -> {
                _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = true)
                startHeartbeat()
                launchSubscriptions()
            }
            else -> {
                _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false)
                _events.emit(WalletEvent.Error("Connection lost"))
            }
        }
    }

    /**
     * Refresh the servers state from storage
     */
    private fun refreshServersState() {
        _serversState.value =
            ServersState(
                servers = repository.getAllElectrumServers(),
                activeServerId = repository.getActiveServerId(),
            )
    }

    /**
     * Import a wallet with full configuration
     */
    fun importWallet(config: WalletImportConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = repository.importWallet(config)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(WalletEvent.WalletImported)
                    // Auto-trigger full sync for the newly imported wallet
                    if (_uiState.value.isConnected) {
                        sync()
                    }
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Generate a new wallet with a fresh BIP39 mnemonic.
     * Uses BDK's Mnemonic constructor which sources entropy from the platform's
     * cryptographically secure random number generator (getrandom on Linux/Android).
     */
    fun generateWallet(config: WalletImportConfig) {
        importWallet(config)
    }

    /**
     * Connect to an Electrum server
     * Automatically enables/disables Tor based on server type
     */
    fun connectToElectrum(config: ElectrumConfig) {
        val previousJob = connectionJob
        stopBackgroundSync()
        stopHeartbeat()
        repository.setUserDisconnected(false)
        connectionJob =
            viewModelScope.launch {
                previousJob?.cancelAndJoin()
                _uiState.value = _uiState.value.copy(isConnecting = true, isConnected = false, error = null, serverVersion = null)

                try {
                    // Auto-enable Tor for .onion addresses, auto-disable for clearnet
                    // (but keep Tor alive if fee/price source is .onion)
                    val otherNeedsTor = isFeeSourceOnion() || isPriceSourceOnion()
                    val shouldKeepTorRunning = config.isOnionAddress() || otherNeedsTor
                    disconnectForServerSwitch(shouldKeepTorRunning)
                    if (config.isOnionAddress()) {
                        repository.setTorEnabled(true)
                    } else if (repository.isTorEnabled()) {
                        // Switching to clearnet — Electrum won't use Tor proxy
                        repository.setTorEnabled(false)
                        // Only stop the Tor service if nothing else needs it
                        if (!otherNeedsTor) {
                            torManager.stop()
                        }
                    }
                    val needsTor = config.isOnionAddress()

                    if (needsTor && !torManager.isReady()) {
                        torManager.start()

                        if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                            val msg = if (torState.value.status == TorStatus.ERROR) {
                                "Tor failed to start: ${torState.value.statusMessage}"
                            } else {
                                "Tor connection timed out"
                            }
                            _uiState.value = _uiState.value.copy(isConnecting = false, error = msg)
                            _events.emit(WalletEvent.Error(msg))
                            return@launch
                        }
                        // Brief settle time for the SOCKS proxy after bootstrap
                        delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                    }

                    // Connect with timeout
                    val timeoutMs = if (needsTor) CONNECTION_TIMEOUT_TOR_MS else CONNECTION_TIMEOUT_CLEARNET_MS
                    val result =
                        withTimeoutOrNull(timeoutMs) {
                            repository.connectToElectrum(config)
                        }

                    when (result) {
                        null -> {
                            // Update UI immediately — don't wait for disconnect IO
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = false,
                                    error = "Connection timed out",
                                )
                            _events.emit(WalletEvent.Error("Connection timed out"))
                            launch(Dispatchers.IO) { repository.disconnect() }
                            if (_autoSwitchServer.value) attemptAutoSwitch()
                        }
                        is WalletResult.Success<*> -> {
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = true,
                                    serverVersion = null,
                                )
                            refreshServersState()
                            _events.emit(WalletEvent.Connected)
                            val serverVersion =
                                withContext(Dispatchers.IO) {
                                    repository.getServerVersion()
                                }
                            _uiState.value = _uiState.value.copy(serverVersion = serverVersion)
                            // Refresh fee estimates (server may support sub-sat fees)
                            fetchFeeEstimates()
                            startHeartbeat()
                            // Smart sync + real-time subscriptions (Sparrow-style).
                            // Runs in the background — does NOT block the connection
                            // flow. Creates a third upstream socket for push
                            // notifications. Over Tor this takes 3-10s extra.
                            launchSubscriptions()
                        }
                        is WalletResult.Error -> {
                            _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false)
                            // Check the full cause chain for TOFU exceptions.
                            // SSLSocket.startHandshake() wraps TrustManager exceptions
                            // in SSLHandshakeException, so we need to unwrap.
                            val firstUse = result.exception?.findCause<CertificateFirstUseException>()
                            val mismatch = result.exception?.findCause<CertificateMismatchException>()
                            when {
                                firstUse != null -> {
                                    _certDialogState.value =
                                        CertDialogState(
                                            certInfo = firstUse.certInfo,
                                            isFirstUse = true,
                                            pendingConfig = config,
                                        )
                                }
                                mismatch != null -> {
                                    _certDialogState.value =
                                        CertDialogState(
                                            certInfo = mismatch.certInfo,
                                            isFirstUse = false,
                                            oldFingerprint = mismatch.storedFingerprint,
                                            pendingConfig = config,
                                        )
                                }
                                else -> {
                                    _uiState.value = _uiState.value.copy(error = result.message)
                                    _events.emit(WalletEvent.Error(result.message))
                                    if (_autoSwitchServer.value) attemptAutoSwitch()
                                }
                            }
                        }
                    }
                } finally {
                    connectionJob = null
                }
            }
    }

    private suspend fun disconnectForServerSwitch(shouldKeepTorRunning: Boolean) {
        withContext(Dispatchers.IO) {
            repository.disconnect()
        }

        val previousServerUsedTor =
            repository.getElectrumConfig()?.isOnionAddress() == true || repository.isTorEnabled()
        if (previousServerUsedTor && !shouldKeepTorRunning) {
            torManager.stop()
        }
    }

    /**
     * Quick sync wallet with the blockchain (revealed addresses only)
     * Used by balance screen refresh
     */
    fun sync() {
        viewModelScope.launch {
            // Watch address wallets use Electrum-only sync
            if (repository.isWatchAddressWallet()) {
                val activeId = repository.getActiveWalletId() ?: return@launch
                when (val result = repository.syncWatchAddress(activeId)) {
                    is WalletResult.Success -> _events.emit(WalletEvent.SyncCompleted)
                    is WalletResult.Error -> _events.emit(WalletEvent.Error(result.message))
                }
                return@launch
            }
            when (val result = repository.sync()) {
                is WalletResult.Success -> {
                    _events.emit(WalletEvent.SyncCompleted)
                }
                is WalletResult.Error -> {
                    // Mutex skip is not a real error — another sync is already
                    // running and will deliver the result. Silently ignore it.
                    if (!result.message.contains("already in progress")) {
                        _events.emit(WalletEvent.Error(result.message))
                    }
                }
            }
        }
    }

    /**
     * Attempt to connect to the next available saved server (round-robin).
     * Skips the currently active (failed) server and tries each remaining
     * server once. Stops on first successful connection.
     */
    private fun attemptAutoSwitch() {
        viewModelScope.launch {
            val currentId = _serversState.value.activeServerId
            val servers = _serversState.value.servers
            if (servers.size < 2) return@launch

            // Build candidate list: all servers except the one that just failed
            val candidates = servers.filter { it.id != currentId && it.id != null }
            if (candidates.isEmpty()) return@launch

            for (candidate in candidates) {
                val id = candidate.id ?: continue
                // connectToServer updates UI state, starts background sync, etc.
                connectToServer(id)
                // Wait for connection attempt to finish
                connectionJob?.join()
                if (_uiState.value.isConnected) {
                    _events.emit(WalletEvent.Error("Auto-switched to ${candidate.displayName()}"))
                    return@launch
                }
            }
            // All candidates failed
            _events.emit(WalletEvent.Error("Auto-switch failed: no reachable servers"))
        }
    }

    /**
     * Stop the background sync loop.
     */
    private fun stopBackgroundSync() {
        backgroundSyncJob?.cancel()
        backgroundSyncJob = null
    }

    /**
     * Start a lightweight heartbeat loop that pings the server every ~60s
     * via the proxy's direct socket. Does NOT hold the sync mutex, so it
     * runs independently of background sync. Detects dead servers in ~2min
     * (2 consecutive failures) instead of ~10min (background sync alone).
     */
    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob?.cancel()
        var consecutiveFailures = 0
        heartbeatJob =
            viewModelScope.launch {
                while (true) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    if (!_uiState.value.isConnected) break

                    // Ping with a hard timeout so a blocking socket read
                    // can't stall detection beyond HEARTBEAT_PING_TIMEOUT_MS.
                    val alive =
                        withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                            withContext(Dispatchers.IO) {
                                repository.pingServer()
                            }
                        } ?: false

                    if (alive) {
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= HEARTBEAT_MAX_FAILURES) {
                            if (walletState.value.isFullSyncing) {
                                consecutiveFailures = 0
                                continue
                            }
                            stopBackgroundSync()
                            _uiState.value = _uiState.value.copy(isConnected = false)
                            _events.emit(WalletEvent.Error("Server connection lost"))
                            if (_autoSwitchServer.value) {
                                attemptAutoSwitch()
                            }
                            break
                        }
                    }
                }
            }
    }

    /**
     * Stop the heartbeat loop.
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Launch real-time subscriptions in the background (fire-and-forget).
     *
     * Creates a third upstream socket for Electrum push notifications.
     * Over Tor this takes 3-10s to establish the circuit, so it MUST NOT
     * block the connection flow. If subscriptions fail (e.g., Tor circuit
     * doesn't establish), the background sync polling covers us.
     *
     * Also handles the initial sync: if needsFullSync is set (first import),
     * runs full sync first. Otherwise compares subscription statuses to
     * persisted cache and only syncs if changes were detected.
     */
    private fun launchSubscriptions() {
        viewModelScope.launch {
            val subResult =
                withContext(Dispatchers.IO) {
                    repository.startRealTimeSubscriptions()
                }
            when (subResult) {
                WalletRepository.SubscriptionResult.SYNCED,
                WalletRepository.SubscriptionResult.FULL_SYNCED,
                ->
                    _events.emit(WalletEvent.SyncCompleted)
                WalletRepository.SubscriptionResult.NO_CHANGES -> { }
                WalletRepository.SubscriptionResult.FAILED -> {
                    // Subscription socket failed (common over Tor).
                    // Background sync polling is already running as fallback.
                    // Run a one-time sync so the wallet is up-to-date.
                    when (val syncResult = repository.sync()) {
                        is WalletResult.Success ->
                            _events.emit(WalletEvent.SyncCompleted)
                        is WalletResult.Error -> {
                            if (!syncResult.message.contains("already in progress")) {
                                _events.emit(WalletEvent.Error(syncResult.message))
                            }
                        }
                    }
                }
            }
            _initialSyncComplete.value = true
        }
    }

    /**
     * Full sync for a specific wallet (address discovery scan).
     * Used by Manage Wallets screen for manual full rescan.
     * If the wallet is not active, switches to it first.
     */
    fun fullSync(walletId: String) {
        fullSyncJob?.cancel()
        fullSyncCancelRequested = false
        val newJob =
            viewModelScope.launch {
            _syncingWalletId.value = walletId

                try {
                    // Switch to the wallet if it's not already active
                    val activeId = repository.getActiveWalletId()
                    if (walletId != activeId) {
                        when (val switchResult = repository.switchWallet(walletId)) {
                            is WalletResult.Success -> {
                                _events.emit(WalletEvent.WalletSwitched)
                            }
                            is WalletResult.Error -> {
                                _events.emit(WalletEvent.Error(switchResult.message))
                                return@launch
                            }
                        }
                    }

                    // Watch address wallets use Electrum-only sync
                    val result =
                        if (repository.isWatchAddressWallet()) {
                            repository.syncWatchAddress(walletId)
                        } else {
                            repository.requestFullSync(walletId)
                        }

                    if (fullSyncCancelRequested) return@launch

                    when (result) {
                        is WalletResult.Success -> {
                            refreshWalletLastFullSyncTimes()
                            _events.emit(WalletEvent.SyncCompleted)
                        }
                        is WalletResult.Error -> {
                            _events.emit(WalletEvent.Error(result.message))
                        }
                    }
                } catch (_: CancellationException) {
                    // User cancelled the manual full sync.
                } finally {
                    _syncingWalletId.value = null
                    if (fullSyncJob === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                        fullSyncJob = null
                    }
                    fullSyncCancelRequested = false
                }
            }
        fullSyncJob = newJob
    }

    fun cancelFullSync() {
        val serverId = _serversState.value.activeServerId ?: repository.getActiveServerId()
        val jobToCancel = fullSyncJob
        fullSyncCancelRequested = true
        fullSyncJob = null
        _syncingWalletId.value = null
        stopBackgroundSync()
        stopHeartbeat()
        _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false, serverVersion = null, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            jobToCancel?.cancel()
            repository.setUserDisconnected(false)
            repository.abortActiveFullSync()
            if (serverId != null) {
                withContext(Dispatchers.Main) {
                    connectToServer(serverId)
                }
            }
        }
    }

    /**
     * Get a new receiving address
     */
    fun getNewAddress() {
        viewModelScope.launch {
            when (val result = repository.getNewAddress()) {
                is WalletResult.Success -> {
                    refreshAddressBook()
                    _events.emit(WalletEvent.AddressGenerated(result.data))
                }
                is WalletResult.Error -> {
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Save a label for an address
     */
    fun saveAddressLabel(
        address: String,
        label: String,
    ) {
        repository.saveAddressLabel(address, label)
        refreshAddressBook()
        refreshLabelSnapshots()
    }

    /**
     * Delete a label for an address
     */
    fun deleteAddressLabel(address: String) {
        repository.deleteAddressLabel(address)
        refreshAddressBook()
        refreshLabelSnapshots()
    }

    /**
     * Get all address labels
     */
    fun getAllAddressLabels(): Map<String, String> {
        return _addressLabels.value
    }

    /**
     * Get all transaction labels
     */
    fun getAllTransactionLabels(): Map<String, String> {
        return _transactionLabels.value
    }

    /**
     * Save a label for a transaction
     */
    fun saveTransactionLabel(
        txid: String,
        label: String,
    ) {
        repository.saveTransactionLabel(txid, label)
        refreshLabelSnapshots()
    }

    /**
     * Get all addresses (receive, change, used)
     */
    fun getAllAddresses(): Triple<List<WalletAddress>, List<WalletAddress>, List<WalletAddress>> {
        return _allAddresses.value
    }

    /**
     * Get all UTXOs
     */
    fun getAllUtxos(): List<UtxoInfo> {
        return _allUtxos.value
    }

    /**
     * Refresh the UTXO list asynchronously on IO.
     * Called after freeze/unfreeze since that changes a local flag
     * that doesn't trigger a walletState emission.
     */
    fun refreshUtxos() {
        viewModelScope.launch(Dispatchers.IO) {
            _allUtxos.value = repository.getAllUtxos()
        }
    }

    /**
     * Freeze/unfreeze a UTXO
     */
    fun setUtxoFrozen(
        outpoint: String,
        frozen: Boolean,
    ) {
        repository.setUtxoFrozen(outpoint, frozen)
        refreshUtxos()
    }

    // ==================== BIP 329 Labels ====================

    /**
     * Get label counts (address, transaction) for a wallet.
     */
    fun getLabelCounts(walletId: String): Pair<Int, Int> {
        return repository.getLabelCounts(walletId)
    }

    fun getBitcoinBip329LabelsContent(
        walletId: String,
        includeNetworkTag: Boolean = false,
    ): String {
        if (!includeNetworkTag) {
            return repository.exportBip329Labels(walletId)
        }

        val metadata = secureStorage.getWalletMetadata(walletId)
        val origin = metadata?.let {
            Bip329Labels.buildOrigin(
                addressType = it.addressType,
                fingerprint = it.masterFingerprint,
            )
        }
        return Bip329Labels.export(
            addressLabels = repository.getAllAddressLabelsForWallet(walletId),
            transactionLabels = repository.getAllTransactionLabelsForWallet(walletId),
            origin = origin,
            network = Bip329LabelNetwork.BITCOIN,
        )
    }

    fun importBitcoinBip329LabelsFromContent(
        walletId: String,
        content: String,
        defaultScope: Bip329LabelScope = Bip329LabelScope.BITCOIN,
    ): Int {
        val result = Bip329Labels.import(content, defaultScope)

        for ((address, label) in result.bitcoinAddressLabels) {
            repository.saveAddressLabelForWallet(walletId, address, label)
        }

        for ((txid, label) in result.bitcoinTransactionLabels) {
            repository.saveTransactionLabelForWallet(walletId, txid, label)
        }

        for ((outpoint, spendable) in result.outputSpendable) {
            if (!spendable) {
                secureStorage.freezeUtxo(walletId, outpoint)
            } else {
                secureStorage.unfreezeUtxo(walletId, outpoint)
            }
        }

        refreshActiveWalletSnapshotsIfNeeded(walletId)
        return result.totalBitcoinLabelsImported
    }

    /**
     * Set a pre-selected UTXO for the Send screen (from AllUtxosScreen)
     */
    fun setPreSelectedUtxo(utxo: UtxoInfo) {
        _preSelectedUtxo.value = utxo
    }

    /**
     * Clear the pre-selected UTXO (after SendScreen has consumed it)
     */
    fun clearPreSelectedUtxo() {
        _preSelectedUtxo.value = null
    }

    /**
     * Update send screen draft state (persists while app is open)
     */
    fun updateSendScreenDraft(draft: SendScreenDraft) {
        _sendScreenDraft.value = draft
    }

    /**
     * Set a pending send input from an external intent or NFC scan.
     * IbisWalletApp will consume it after unlock and navigate to Send.
     */
    fun setPendingSendInput(input: String?) {
        _pendingSendInput.value = input
    }

    /**
     * Consume the pending send input (called after navigation to Send).
     */
    fun consumePendingSendInput() {
        _pendingSendInput.value = null
    }

    /**
     * Clear send screen draft (called on successful send or app close)
     */
    private fun clearSendScreenDraft() {
        _sendScreenDraft.value = SendScreenDraft()
    }

    /**
     * Send Bitcoin to an address with specified fee rate
     * @param selectedUtxos Optional list of specific UTXOs to spend from (coin control)
     * @param label Optional label for the transaction
     * @param isMaxSend If true, resolves the largest exact send amount with fees on top
     */
    fun sendBitcoin(
        recipientAddress: String,
        amountSats: ULong,
        feeRate: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: Long? = null,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, sendStatus = null, error = null)

            when (
                val result =
                    repository.sendBitcoin(
                        recipientAddress,
                        amountSats,
                        feeRate,
                        selectedUtxos,
                        label,
                        isMaxSend,
                        precomputedFeeSats = precomputedFeeSats?.toULong(),
                        onProgress = { status ->
                            _uiState.value = _uiState.value.copy(sendStatus = status)
                        },
                    )
            ) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false, sendStatus = null)
                    // Clear send screen draft on successful transaction
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.TransactionSent(result.data))
                    // Quick sync to update balance and show the new outgoing tx
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            sendStatus = null,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    suspend fun sendBitcoinForSwap(
        recipientAddress: String,
        amountSats: Long,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
    ): Pair<String, Long> {
        val recipientAmountSats =
            if (isMaxSend) {
                getMaxBitcoinSpendableForSwap(
                    recipientAddress = recipientAddress,
                    feeRate = feeRate,
                    selectedUtxos = selectedUtxos,
                )
            } else {
                amountSats
            }
        return when (
            val result = repository.sendBitcoin(
                recipientAddress = recipientAddress,
                amountSats = recipientAmountSats.toULong(),
                feeRateSatPerVb = feeRate,
                selectedUtxos = selectedUtxos,
                isMaxSend = false,
                onProgress = {},
            )
        ) {
            is WalletResult.Success -> {
                sync()
                result.data to recipientAmountSats
            }
            is WalletResult.Error -> {
                val message = result.exception?.message?.takeIf { it.isNotBlank() } ?: result.message
                throw Exception(message, result.exception)
            }
        }
    }

    suspend fun getMaxBitcoinSpendableForSwap(
        recipientAddress: String,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ): Long {
        return repository.dryRunBuildTx(
            recipientAddress = recipientAddress,
            amountSats = 0u,
            feeRateSatPerVb = feeRate,
            selectedUtxos = selectedUtxos,
            isMaxSend = true,
        )?.recipientAmountSats?.takeIf { it > 0 }
            ?: throw Exception("Unable to compute max spendable Bitcoin amount")
    }

    suspend fun previewBitcoinFundingForSwap(
        recipientAddress: String,
        amountSats: Long,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
    ): DryRunResult? {
        return repository.dryRunBuildTx(
            recipientAddress = recipientAddress,
            amountSats = amountSats.toULong(),
            feeRateSatPerVb = feeRate,
            selectedUtxos = selectedUtxos,
            isMaxSend = isMaxSend,
        )
    }

    /**
     * Estimate the actual fee using BDK's TxBuilder (dry-run, no network).
     * Call this when the user changes amount, fee rate, or UTXO selection.
     */
    fun estimateFee(
        recipientAddress: String,
        amountSats: ULong,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
        isMaxSend: Boolean = false,
    ) {
        dryRunJob?.cancel()
        dryRunJob =
            viewModelScope.launch {
                val result =
                    repository.dryRunBuildTx(
                        recipientAddress = recipientAddress,
                        amountSats = amountSats,
                        feeRateSatPerVb = feeRate,
                        selectedUtxos = selectedUtxos,
                        isMaxSend = isMaxSend,
                    )
                _dryRunResult.value = result
            }
    }

    fun clearDryRunResult() {
        dryRunJob?.cancel()
        _dryRunResult.value = null
    }

    /** Estimate fee for a multi-recipient transaction. */
    fun estimateFeeMulti(
        recipients: List<Recipient>,
        feeRate: Double,
        selectedUtxos: List<UtxoInfo>? = null,
    ) {
        dryRunJob?.cancel()
        dryRunJob =
            viewModelScope.launch {
                _dryRunResult.value = repository.dryRunBuildTx(recipients, feeRate, selectedUtxos)
            }
    }

    /** Send to multiple recipients in a single transaction. */
    fun sendBitcoinMulti(
        recipients: List<Recipient>,
        feeRate: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        precomputedFeeSats: Long? = null,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, sendStatus = null, error = null)
            when (
                val result =
                    repository.sendBitcoin(
                        recipients,
                        feeRate,
                        selectedUtxos,
                        label,
                        precomputedFeeSats = precomputedFeeSats?.toULong(),
                        onProgress = { status ->
                            _uiState.value = _uiState.value.copy(sendStatus = status)
                        },
                    )
            ) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false, sendStatus = null)
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.TransactionSent(result.data))
                    // Quick sync to update balance and show the new outgoing tx
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSending = false, sendStatus = null, error = result.message)
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /** Create PSBT with multiple recipients for watch-only wallets. */
    fun createPsbtMulti(
        recipients: List<Recipient>,
        feeRate: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        precomputedFeeSats: Long? = null,
    ) {
        viewModelScope.launch {
            _psbtState.value = _psbtState.value.copy(isCreating = true, error = null)
            when (
                val result =
                    repository.createPsbt(
                        recipients,
                        feeRate,
                        selectedUtxos,
                        label,
                        precomputedFeeSats = precomputedFeeSats?.toULong(),
                    )
            ) {
                is WalletResult.Success -> {
                    val details = result.data
                    _psbtState.value =
                        _psbtState.value.copy(
                            isCreating = false,
                            unsignedPsbtBase64 = details.psbtBase64,
                            pendingLabel = label,
                            actualFeeSats = details.feeSats,
                            recipientAddress = details.recipientAddress,
                            recipientAmountSats = details.recipientAmountSats,
                            changeAmountSats = details.changeAmountSats,
                            totalInputSats = details.totalInputSats,
                        )
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.PsbtCreated(details.psbtBase64))
                }
                is WalletResult.Error -> {
                    _psbtState.value = _psbtState.value.copy(isCreating = false, error = result.message)
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Create an unsigned PSBT for a watch-only wallet.
     * Does not sign or broadcast - the PSBT is exported for external signing.
     */
    fun createPsbt(
        recipientAddress: String,
        amountSats: ULong,
        feeRate: Double = 1.0,
        selectedUtxos: List<UtxoInfo>? = null,
        label: String? = null,
        isMaxSend: Boolean = false,
        precomputedFeeSats: Long? = null,
    ) {
        viewModelScope.launch {
            _psbtState.value = _psbtState.value.copy(isCreating = true, error = null)

            when (
                val result =
                    repository.createPsbt(
                        recipientAddress,
                        amountSats,
                        feeRate,
                        selectedUtxos,
                        label,
                        isMaxSend,
                        precomputedFeeSats = precomputedFeeSats?.toULong(),
                    )
            ) {
                is WalletResult.Success -> {
                    val details = result.data
                    _psbtState.value =
                        _psbtState.value.copy(
                            isCreating = false,
                            unsignedPsbtBase64 = details.psbtBase64,
                            pendingLabel = label,
                            actualFeeSats = details.feeSats,
                            recipientAddress = details.recipientAddress,
                            recipientAmountSats = details.recipientAmountSats,
                            changeAmountSats = details.changeAmountSats,
                            totalInputSats = details.totalInputSats,
                        )
                    clearSendScreenDraft()
                    _events.emit(WalletEvent.PsbtCreated(details.psbtBase64))
                }
                is WalletResult.Error -> {
                    _psbtState.value =
                        _psbtState.value.copy(
                            isCreating = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Store signed transaction data for user confirmation before broadcasting.
     * Called after scanning the signed PSBT/tx back from the hardware wallet.
     */
    fun setSignedTransactionData(data: String) {
        _psbtState.value = _psbtState.value.copy(signedData = data, error = null)
    }

    /**
     * Broadcast the signed transaction after user confirmation.
     * Uses the signed data previously stored via setSignedTransactionData().
     */
    fun confirmBroadcast() {
        val data = _psbtState.value.signedData ?: return
        viewModelScope.launch {
            _psbtState.value = _psbtState.value.copy(isBroadcasting = true, broadcastStatus = null, error = null)
            val pendingLabel = _psbtState.value.pendingLabel
            val unsignedPsbt = _psbtState.value.unsignedPsbtBase64

            val onProgress: (String) -> Unit = { status ->
                _psbtState.value = _psbtState.value.copy(broadcastStatus = status)
            }

            // Auto-detect format: base64 PSBT vs raw tx hex
            val isHex = data.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            val result =
                if (isHex && data.length % 2 == 0 && data.length > 20) {
                    repository.broadcastRawTx(data, pendingLabel, onProgress)
                } else {
                    repository.broadcastSignedPsbt(data, unsignedPsbt, pendingLabel, onProgress)
                }

            when (result) {
                is WalletResult.Success -> {
                    _psbtState.value = PsbtState() // Reset PSBT state
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.TransactionSent(result.data))
                    // Quick sync to update balance and show the broadcast tx
                    sync()
                }
                is WalletResult.Error -> {
                    _psbtState.value =
                        _psbtState.value.copy(
                            isBroadcasting = false,
                            broadcastStatus = null,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Cancel the pending broadcast and return to the PSBT QR display.
     * Clears signed data but keeps the unsigned PSBT for re-scanning.
     */
    fun cancelBroadcast() {
        _psbtState.value = _psbtState.value.copy(signedData = null, error = null)
    }

    /**
     * Clear the PSBT state (e.g., when navigating away from PSBT screen)
     */
    fun clearPsbtState() {
        _psbtState.value = PsbtState()
    }

    /**
     * Broadcast a manually provided signed transaction (raw hex or signed PSBT).
     * Standalone — the transaction may not belong to any loaded wallet.
     */
    fun broadcastManualTransaction(data: String) {
        viewModelScope.launch {
            _manualBroadcastState.value = ManualBroadcastState(isBroadcasting = true)
            val result =
                repository.broadcastManualData(data) { status ->
                    _manualBroadcastState.value =
                        _manualBroadcastState.value.copy(
                            broadcastStatus = status,
                        )
                }
            when (result) {
                is WalletResult.Success -> {
                    _manualBroadcastState.value = ManualBroadcastState(txid = result.data)
                }
                is WalletResult.Error -> {
                    _manualBroadcastState.value = ManualBroadcastState(error = result.message)
                }
            }
        }
    }

    /**
     * Clear the manual broadcast state (e.g., when navigating away)
     */
    fun clearManualBroadcastState() {
        _manualBroadcastState.value = ManualBroadcastState()
    }

    /**
     * Bump fee of an unconfirmed transaction using RBF
     */
    fun bumpFee(
        txid: String,
        newFeeRate: Double,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            when (val result = repository.bumpFee(txid, newFeeRate)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.FeeBumped(result.data))
                    // Quick sync to update balance and show the replacement tx
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Speed up an incoming transaction using CPFP
     */
    fun cpfp(
        parentTxid: String,
        feeRate: Double,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            when (val result = repository.cpfp(parentTxid, feeRate)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.CpfpCreated(result.data))
                    // Quick sync to update balance and show the CPFP tx
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Cancel an unconfirmed sent transaction by redirecting all funds back to the wallet via RBF.
     */
    fun redirectTransaction(
        txid: String,
        newFeeRate: Double,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            when (val result = repository.redirectTransaction(txid, newFeeRate)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                    _events.emit(WalletEvent.TransactionRedirected(result.data))
                    sync()
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Check if a transaction can be bumped with RBF
     */
    fun canBumpFee(txid: String): Boolean {
        return repository.canBumpFee(txid)
    }

    /**
     * Check if a transaction can be sped up with CPFP
     */
    fun canCpfp(txid: String): Boolean {
        return repository.canCpfp(txid)
    }

    /**
     * Fetch transaction vsize from Electrum server
     * Returns the fractional vsize (weight / 4.0) from the network
     */
    suspend fun fetchTransactionVsize(txid: String): Double? {
        return repository.fetchTransactionVsizeFromElectrum(txid)
    }

    /**
     * Delete a specific wallet by ID
     */
    fun deleteWallet(walletId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = repository.deleteWallet(walletId)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    refreshWalletLastFullSyncTimes()
                    _events.emit(WalletEvent.WalletDeleted)
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Edit wallet metadata (name and optionally fingerprint for watch-only)
     */
    fun editWallet(
        walletId: String,
        newName: String,
        newGapLimit: Int,
        newFingerprint: String? = null,
    ) {
        repository.editWallet(walletId, newName, newGapLimit, newFingerprint)
    }

    /**
     * Set whether a wallet requires app authentication before opening.
     */
    fun setWalletLocked(walletId: String, locked: Boolean) {
        repository.setWalletLocked(walletId, locked)
    }

    /**
     * Reorder wallets to the given ID order.
     * Persists the new order and updates the wallet list for all screens.
     */
    fun reorderWallets(orderedIds: List<String>) {
        repository.reorderWallets(orderedIds)
        refreshWalletLastFullSyncTimes()
    }

    /**
     * Switch to a different wallet
     */
    fun switchWallet(walletId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            _initialSyncComplete.value = false

            when (val result = repository.switchWallet(walletId)) {
                is WalletResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    refreshCurrentWalletSnapshots()
                    refreshWalletLastFullSyncTimes()
                    _events.emit(WalletEvent.WalletSwitched)
                    // For normal wallets, restarting subscriptions is enough:
                    // it resubscribes the new addresses and runs sync only if needed.
                    // Watch-address wallets have no subscription lifecycle, so sync directly.
                    if (_uiState.value.isConnected) {
                        if (repository.isWatchAddressWallet()) {
                            sync()
                            _initialSyncComplete.value = true
                        } else {
                            launchSubscriptions()
                        }
                    } else {
                        _initialSyncComplete.value = true
                    }
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    /**
     * Get stored Electrum config (active server)
     */
    fun getElectrumConfig(): ElectrumConfig? = repository.getElectrumConfig()

    /**
     * Save a new Electrum server
     */
    fun saveServer(config: ElectrumConfig): ElectrumConfig {
        val savedConfig = repository.saveElectrumServer(config)
        refreshServersState()
        return savedConfig
    }

    /**
     * Delete an Electrum server
     */
    fun deleteServer(serverId: String) {
        val wasActive = repository.getActiveServerId() == serverId
        repository.deleteElectrumServer(serverId)
        if (wasActive) {
            // Stop background sync and cancel any pending connection
            stopBackgroundSync()
            connectionJob?.cancel()
            connectionJob = null
            _uiState.value =
                _uiState.value.copy(
                    isConnecting = false,
                    isConnected = false,
                    serverVersion = null,
                    error = null,
                )
        }
        refreshServersState()
        _events.tryEmit(WalletEvent.ServerDeleted)
    }

    /**
     * Connect to a saved server by ID
     * Automatically enables/disables Tor based on server type
     */
    fun connectToServer(serverId: String) {
        val previousJob = connectionJob
        stopBackgroundSync()
        stopHeartbeat()
        repository.setUserDisconnected(false)
        // Update active server immediately so the UI shows the new server while connecting
        _serversState.value = _serversState.value.copy(activeServerId = serverId)
        connectionJob =
            viewModelScope.launch {
                previousJob?.cancelAndJoin()
                _uiState.value = _uiState.value.copy(isConnecting = true, isConnected = false, error = null, serverVersion = null)

                try {
                    // Get the server config to check if it's an onion address
                    val servers = repository.getAllElectrumServers()
                    val serverConfig = servers.find { it.id == serverId }

                    // Auto-enable Tor for .onion addresses, auto-disable for clearnet
                    // (but keep Tor alive if fee/price source is .onion)
                    val isOnion = serverConfig?.isOnionAddress() == true
                    val otherNeedsTor = isFeeSourceOnion() || isPriceSourceOnion()
                    val shouldKeepTorRunning = isOnion || otherNeedsTor
                    disconnectForServerSwitch(shouldKeepTorRunning)
                    if (isOnion) {
                        repository.setTorEnabled(true)
                    } else if (repository.isTorEnabled()) {
                        // Switching to clearnet — Electrum won't use Tor proxy
                        repository.setTorEnabled(false)
                        // Only stop the Tor service if nothing else needs it
                        if (!otherNeedsTor) {
                            torManager.stop()
                        }
                    }
                    if (isOnion && !torManager.isReady()) {
                        torManager.start()

                        if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                            val msg = if (torState.value.status == TorStatus.ERROR) {
                                "Tor failed to start: ${torState.value.statusMessage}"
                            } else {
                                "Tor connection timed out"
                            }
                            _uiState.value = _uiState.value.copy(isConnecting = false, error = msg)
                            _events.emit(WalletEvent.Error(msg))
                            return@launch
                        }
                        // Brief settle time for the SOCKS proxy after bootstrap
                        delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                    }

                    // Connect with timeout
                    val timeoutMs = if (isOnion) CONNECTION_TIMEOUT_TOR_MS else CONNECTION_TIMEOUT_CLEARNET_MS
                    val result =
                        withTimeoutOrNull(timeoutMs) {
                            repository.connectToServer(serverId)
                        }

                    when (result) {
                        null -> {
                            // Update UI immediately — don't wait for disconnect IO
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = false,
                                    error = "Connection timed out",
                                )
                            _events.emit(WalletEvent.Error("Connection timed out"))
                            launch(Dispatchers.IO) { repository.disconnect() }
                            if (_autoSwitchServer.value) attemptAutoSwitch()
                        }
                        is WalletResult.Success -> {
                            // Query server software version (e.g. "Fulcrum 1.10.0")
                            val serverVersion =
                                withContext(Dispatchers.IO) {
                                    repository.getServerVersion()
                                }
                            _uiState.value =
                                _uiState.value.copy(
                                    isConnecting = false,
                                    isConnected = true,
                                    serverVersion = serverVersion,
                                )
                            refreshServersState()
                            _events.emit(WalletEvent.Connected)
                            // Refresh fee estimates (server may support sub-sat fees)
                            fetchFeeEstimates()
                            startHeartbeat()
                            launchSubscriptions()
                        }
                        is WalletResult.Error -> {
                            _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false)
                            val firstUse = result.exception?.findCause<CertificateFirstUseException>()
                            val mismatch = result.exception?.findCause<CertificateMismatchException>()
                            when {
                                firstUse != null -> {
                                    _certDialogState.value =
                                        CertDialogState(
                                            certInfo = firstUse.certInfo,
                                            isFirstUse = true,
                                            pendingServerId = serverId,
                                        )
                                }
                                mismatch != null -> {
                                    _certDialogState.value =
                                        CertDialogState(
                                            certInfo = mismatch.certInfo,
                                            isFirstUse = false,
                                            oldFingerprint = mismatch.storedFingerprint,
                                            pendingServerId = serverId,
                                        )
                                }
                                else -> {
                                    _uiState.value = _uiState.value.copy(error = result.message)
                                    _events.emit(WalletEvent.Error(result.message))
                                    if (_autoSwitchServer.value) attemptAutoSwitch()
                                }
                            }
                        }
                    }
                } finally {
                    connectionJob = null
                }
            }
    }

    /**
     * Get the key material (mnemonic and/or extended public key) for a wallet
     */
    fun getKeyMaterial(walletId: String): WalletRepository.WalletKeyMaterial? = repository.getKeyMaterial(walletId)

    /**
     * Get the last full sync timestamp for a wallet
     */
    fun getLastFullSyncTime(walletId: String): Long? =
        _walletLastFullSyncTimes.value[walletId] ?: repository.getLastFullSyncTime(walletId)

    // ==================== Wallet Export/Import ====================

    /**
     * Export a wallet to a JSON backup file, optionally encrypted with AES-256-GCM
     */
    fun exportWallet(
        walletId: String,
        uri: Uri,
        includeLabels: Boolean,
        includeServerSettings: Boolean,
        password: String?,
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val walletMetadata =
                    repository.getWalletMetadata(walletId)
                        ?: throw IllegalStateException("Wallet not found")
                val keyMaterial =
                    repository.getKeyMaterial(walletId)
                        ?: throw IllegalStateException("Key material not found")

                // Build the wallet payload JSON
                val payloadJson =
                    JSONObject().apply {
                        put("version", 2)
                        put("encrypted", password != null)
                        put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

                        put(
                            "wallet",
                            JSONObject().apply {
                                put("name", walletMetadata.name)
                                put("addressType", walletMetadata.addressType.name)
                                put("derivationPath", walletMetadata.derivationPath)
                                put("network", walletMetadata.network.name)
                                put("isWatchOnly", walletMetadata.isWatchOnly)
                                put("createdAt", walletMetadata.createdAt)
                                put("seedFormat", walletMetadata.seedFormat.name)
                            },
                        )

                        put(
                            "keyMaterial",
                            JSONObject().apply {
                                if (keyMaterial.mnemonic != null) put("mnemonic", keyMaterial.mnemonic)
                                if (keyMaterial.extendedPublicKey != null) {
                                    put(
                                        "extendedPublicKey",
                                        keyMaterial.extendedPublicKey,
                                    )
                                }
                            },
                        )

                        if (includeLabels) {
                            val addressLabels = repository.getAllAddressLabelsForWallet(walletId)
                            val txLabels = repository.getAllTransactionLabelsForWallet(walletId)
                            val liquidTxLabels = repository.getAllLiquidTransactionLabelsForWallet(walletId)

                            put(
                                "labels",
                                JSONObject().apply {
                                    put(
                                        "addresses",
                                        JSONObject().apply {
                                            addressLabels.forEach { (addr, label) -> put(addr, label) }
                                        },
                                    )
                                    put(
                                        "transactions",
                                        JSONObject().apply {
                                            txLabels.forEach { (txid, label) -> put(txid, label) }
                                        },
                                    )
                                    put(
                                        "liquidTransactions",
                                        JSONObject().apply {
                                            liquidTxLabels.forEach { (txid, label) -> put(txid, label) }
                                        },
                                    )
                                },
                            )
                        }

                        val liquidTxSources = repository.getAllLiquidTransactionSourcesForWallet(walletId)
                        val liquidSwapDetails = repository.getAllLiquidSwapDetailsForWallet(walletId)
                        if (liquidTxSources.isNotEmpty() || liquidSwapDetails.isNotEmpty()) {
                            put(
                                "liquidMetadata",
                                JSONObject().apply {
                                    put(
                                        "transactionSources",
                                        JSONObject().apply {
                                            liquidTxSources.forEach { (txid, source) -> put(txid, source.name) }
                                        },
                                    )
                                    put(
                                        "swapDetails",
                                        JSONObject().apply {
                                            liquidSwapDetails.forEach { (txid, details) ->
                                                put(
                                                    txid,
                                                    JSONObject().apply {
                                                        put("service", details.service.name)
                                                        put("direction", details.direction.name)
                                                        put("swapId", details.swapId)
                                                        put("role", details.role.name)
                                                        put("depositAddress", details.depositAddress)
                                                        put("receiveAddress", details.receiveAddress)
                                                        put("refundAddress", details.refundAddress)
                                                        put("sendAmountSats", details.sendAmountSats)
                                                        put("expectedReceiveAmountSats", details.expectedReceiveAmountSats)
                                                    },
                                                )
                                            }
                                        },
                                    )
                                },
                            )
                        }

                        if (includeServerSettings) {
                            val servers = repository.getAllElectrumServers()
                            val activeId = repository.getActiveServerId()

                            put(
                                "serverSettings",
                                JSONObject().apply {
                                    put(
                                        "electrumServers",
                                        org.json.JSONArray().apply {
                                            servers.forEach { server ->
                                                put(
                                                    JSONObject().apply {
                                                        put("name", server.name ?: "")
                                                        put("url", server.url)
                                                        put("port", server.port)
                                                        put("useSsl", server.useSsl)
                                                        put("useTor", server.useTor)
                                                        if (server.id == activeId) {
                                                            put("isActive", true)
                                                        }
                                                    },
                                                )
                                            }
                                        },
                                    )
                                    // Only export custom URLs — don't export the type
                                    // selection (e.g. MEMPOOL_SPACE) because restoring it
                                    // would silently enable an external service on import.
                                    val explorerCustomUrl = repository.getCustomMempoolUrl()
                                    if (!explorerCustomUrl.isNullOrBlank()) {
                                        put(
                                            "blockExplorer",
                                            JSONObject().apply {
                                                put("customUrl", explorerCustomUrl)
                                            },
                                        )
                                    }
                                    val feeCustomUrl = repository.getCustomFeeSourceUrl()
                                    if (!feeCustomUrl.isNullOrBlank()) {
                                        put(
                                            "feeSource",
                                            JSONObject().apply {
                                                put("customUrl", feeCustomUrl)
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }

                val outputJson =
                    if (password != null) {
                        // Encrypt the payload
                        val plaintext = payloadJson.toString().toByteArray(Charsets.UTF_8)
                        val encrypted = encryptData(plaintext, password)

                        JSONObject().apply {
                            put("version", 2)
                            put("encrypted", true)
                            put("salt", Base64.encodeToString(encrypted.salt, Base64.NO_WRAP))
                            put("iv", Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
                            put("data", Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
                        }
                    } else {
                        payloadJson
                    }

                // Write to the chosen URI
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(outputJson.toString(2).toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open output stream")
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(WalletEvent.WalletExported(walletMetadata.name))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _events.emit(WalletEvent.Error("Export failed: ${e.message}"))
            }
        }
    }

    // ==================== Full App Backup / Restore ====================

    fun getBackupWalletEntries(): List<github.aeonbtc.ibiswallet.ui.screens.BackupWalletEntry> {
        return repository.getAllWalletIds().mapNotNull { id ->
            val metadata = repository.getWalletMetadata(id) ?: return@mapNotNull null
            github.aeonbtc.ibiswallet.ui.screens.BackupWalletEntry(
                id = id,
                name = metadata.name,
                type = metadata.addressType.displayName,
                isWatchOnly = metadata.isWatchOnly,
            )
        }
    }

    fun exportFullBackup(
        uri: Uri,
        walletIds: List<String>,
        includeLabels: Boolean,
        includeServers: Boolean,
        includeAppSettings: Boolean,
        password: String?,
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val walletsArray = org.json.JSONArray()
                for (walletId in walletIds) {
                    val metadata = repository.getWalletMetadata(walletId) ?: continue
                    val keyMaterial = repository.getKeyMaterial(walletId) ?: continue

                    val walletEntry = JSONObject().apply {
                        put("wallet", JSONObject().apply {
                            put("name", metadata.name)
                            put("addressType", metadata.addressType.name)
                            put("derivationPath", metadata.derivationPath)
                            put("network", metadata.network.name)
                            put("isWatchOnly", metadata.isWatchOnly)
                            put("isLocked", metadata.isLocked)
                            put("createdAt", metadata.createdAt)
                            put("seedFormat", metadata.seedFormat.name)
                            if (metadata.gapLimit != StoredWallet.DEFAULT_GAP_LIMIT) {
                                put("gapLimit", metadata.gapLimit)
                            }
                            metadata.masterFingerprint?.let { put("masterFingerprint", it) }
                        })
                        put("keyMaterial", JSONObject().apply {
                            keyMaterial.mnemonic?.let { put("mnemonic", it) }
                            keyMaterial.extendedPublicKey?.let { put("extendedPublicKey", it) }
                            keyMaterial.watchAddress?.let { put("watchAddress", it) }
                            keyMaterial.privateKey?.let { put("privateKey", it) }
                        })

                        put("walletSettings", JSONObject().apply {
                            put("liquidEnabled", repository.isLiquidEnabledForWallet(walletId))
                            val frozen = repository.getFrozenUtxosForWallet(walletId)
                            if (frozen.isNotEmpty()) {
                                put("frozenUtxos", org.json.JSONArray(frozen.toList()))
                            }
                        })

                        if (includeLabels) {
                            val addrLabels = repository.getAllAddressLabelsForWallet(walletId)
                            val txLabels = repository.getAllTransactionLabelsForWallet(walletId)
                            val liquidTxLabels = repository.getAllLiquidTransactionLabelsForWallet(walletId)
                            put("labels", JSONObject().apply {
                                put("addresses", JSONObject().apply { addrLabels.forEach { (k, v) -> put(k, v) } })
                                put("transactions", JSONObject().apply { txLabels.forEach { (k, v) -> put(k, v) } })
                                put("liquidTransactions", JSONObject().apply { liquidTxLabels.forEach { (k, v) -> put(k, v) } })
                            })

                            val liquidTxSources = repository.getAllLiquidTransactionSourcesForWallet(walletId)
                            val liquidSwapDetails = repository.getAllLiquidSwapDetailsForWallet(walletId)
                            if (liquidTxSources.isNotEmpty() || liquidSwapDetails.isNotEmpty()) {
                                put("liquidMetadata", JSONObject().apply {
                                    put("transactionSources", JSONObject().apply {
                                        liquidTxSources.forEach { (txid, source) -> put(txid, source.name) }
                                    })
                                    put("swapDetails", JSONObject().apply {
                                        liquidSwapDetails.forEach { (txid, details) ->
                                            put(txid, JSONObject().apply {
                                                put("service", details.service.name)
                                                put("direction", details.direction.name)
                                                put("swapId", details.swapId)
                                                put("role", details.role.name)
                                                put("depositAddress", details.depositAddress)
                                                put("receiveAddress", details.receiveAddress)
                                                put("refundAddress", details.refundAddress)
                                                put("sendAmountSats", details.sendAmountSats)
                                                put("expectedReceiveAmountSats", details.expectedReceiveAmountSats)
                                            })
                                        }
                                    })
                                })
                            }
                        }
                    }
                    walletsArray.put(walletEntry)
                }

                val payloadJson = JSONObject().apply {
                    put("ibisFullBackup", true)
                    put("version", 1)
                    put("encrypted", password != null)
                    put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                    put("wallets", walletsArray)

                    if (includeServers) {
                        val servers = repository.getAllElectrumServers()
                        val activeId = repository.getActiveServerId()
                        put("electrumServers", org.json.JSONArray().apply {
                            servers.forEach { server ->
                                put(JSONObject().apply {
                                    put("name", server.name ?: "")
                                    put("url", server.url)
                                    put("port", server.port)
                                    put("useSsl", server.useSsl)
                                    put("useTor", server.useTor)
                                    if (server.id == activeId) put("isActive", true)
                                })
                            }
                        })

                        val liquidServers = repository.getAllLiquidServers()
                        val activeLiquidId = repository.getActiveLiquidServerId()
                        put("liquidServers", org.json.JSONArray().apply {
                            liquidServers.forEach { server ->
                                put(JSONObject().apply {
                                    put("name", server.name)
                                    put("url", server.url)
                                    put("port", server.port)
                                    put("useSsl", server.useSsl)
                                    put("useTor", server.useTor)
                                    if (server.id == activeLiquidId) put("isActive", true)
                                })
                            }
                        })
                    }

                    if (includeAppSettings) {
                        put("appSettings", JSONObject().apply {
                            put("layer1Denomination", repository.getLayer1Denomination())
                            put("layer2Denomination", repository.getLayer2Denomination())
                            put("autoSwitchServer", repository.isAutoSwitchServerEnabled())
                            put("torEnabled", repository.isTorEnabled())
                            put("spendUnconfirmed", repository.getSpendUnconfirmed())
                            put("nfcEnabled", repository.isNfcEnabled())
                            put("privacyMode", repository.getPrivacyMode())
                            put("walletNotificationsEnabled", repository.isWalletNotificationsEnabled())
                            put("mempoolServer", repository.getMempoolServer())
                            repository.getCustomMempoolUrl()?.let { put("mempoolCustomUrl", it) }
                            put("feeSource", repository.getFeeSource())
                            repository.getCustomFeeSourceUrl()?.let { put("feeSourceCustomUrl", it) }
                            put("priceSource", repository.getPriceSource())
                            put("layer2Enabled", repository.isLayer2Enabled())
                            put("liquidTorEnabled", repository.isLiquidTorEnabled())
                            put("boltzApiSource", repository.getBoltzApiSource())
                            put("sideSwapApiSource", repository.getSideSwapApiSource())
                            put("preferredSwapService", repository.getPreferredSwapService().name)
                            put("liquidAutoSwitch", repository.isLiquidAutoSwitchEnabled())
                            put("liquidServerSelectedByUser", repository.hasUserSelectedLiquidServer())
                            put("liquidExplorer", repository.getLiquidExplorer())
                            repository.getCustomLiquidExplorerUrl()?.let { put("liquidExplorerCustomUrl", it) }
                        })
                    }
                }

                val outputJson = if (password != null) {
                    val plaintext = payloadJson.toString().toByteArray(Charsets.UTF_8)
                    val encrypted = encryptData(plaintext, password)
                    JSONObject().apply {
                        put("ibisFullBackup", true)
                        put("version", 1)
                        put("encrypted", true)
                        put("salt", Base64.encodeToString(encrypted.salt, Base64.NO_WRAP))
                        put("iv", Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
                        put("data", Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
                    }
                } else {
                    payloadJson
                }

                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(outputJson.toString(2).toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open output stream")
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
                _fullBackupResultMessage.value = "Successfully exported ${walletIds.size} wallet(s)"
                _events.emit(WalletEvent.WalletExported("Full backup (${walletIds.size} wallets)"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _fullBackupResultMessage.value = "Export failed: ${e.message}"
                _events.emit(WalletEvent.Error("Full backup export failed: ${e.message}"))
            }
        }
    }

    suspend fun parseFullBackup(
        uri: Uri,
        password: String?,
    ): JSONObject {
        return withContext(Dispatchers.IO) {
            val rawBytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                it.readBytes()
            } ?: throw IllegalStateException("Could not read file")

            val fileJson = JSONObject(String(rawBytes, Charsets.UTF_8))

            if (!fileJson.optBoolean("ibisFullBackup", false)) {
                throw IllegalStateException("Not a full Ibis backup file. Use Manage Wallets to restore single-wallet backups.")
            }

            if (fileJson.optBoolean("encrypted", false)) {
                if (password.isNullOrEmpty()) {
                    throw IllegalStateException("This backup is encrypted. Please enter the password.")
                }
                val salt = Base64.decode(fileJson.getString("salt"), Base64.NO_WRAP)
                val iv = Base64.decode(fileJson.getString("iv"), Base64.NO_WRAP)
                val ciphertext = Base64.decode(fileJson.getString("data"), Base64.NO_WRAP)
                val plaintext = decryptData(EncryptedPayload(salt, iv, ciphertext), password)
                JSONObject(String(plaintext, Charsets.UTF_8))
            } else {
                fileJson
            }
        }
    }

    suspend fun importFullBackup(
        backupJson: JSONObject,
        importWallets: Boolean,
        importLabels: Boolean,
        importServers: Boolean,
        importAppSettings: Boolean,
    ): Boolean {
        return try {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            var walletsImported = 0
            var walletsSkipped = 0

            if (importWallets || importLabels) {
                val walletsArray = backupJson.optJSONArray("wallets") ?: org.json.JSONArray()
                val existingMnemonics = repository.getAllWalletIds().mapNotNull { id ->
                    repository.getKeyMaterial(id)?.mnemonic
                }.toSet()

                for (i in 0 until walletsArray.length()) {
                    val entry = walletsArray.getJSONObject(i)
                    val walletObj = entry.getJSONObject("wallet")
                    val keyMaterialObj = entry.getJSONObject("keyMaterial")
                    val mnemonic = keyMaterialObj.optString("mnemonic", "")

                        if (mnemonic.isNotBlank() && mnemonic in existingMnemonics) {
                            walletsSkipped++
                            val existingId = findWalletIdByMnemonic(mnemonic)
                            if (existingId != null) {
                                restoreWalletSettings(existingId, entry)
                                if (importLabels) {
                                    restoreLabelsForWallet(existingId, entry)
                                }
                            }
                            continue
                        }

                        if (importWallets) {
                            try {
                                val parsed = BitcoinUtils.parseBackupJson(walletObj, keyMaterialObj)
                                val network = BitcoinUtils.parseSupportedWalletNetwork(parsed.network)
                                val seedFormat = try {
                                    SeedFormat.valueOf(parsed.seedFormat)
                                } catch (_: Exception) { SeedFormat.BIP39 }

                                val gapLimit = walletObj.optInt("gapLimit", StoredWallet.DEFAULT_GAP_LIMIT)
                                val fingerprint = walletObj.optString("masterFingerprint", "").ifBlank { null }

                                val config = WalletImportConfig(
                                    name = parsed.name,
                                    keyMaterial = parsed.keyMaterial,
                                    addressType = parsed.addressType,
                                    customDerivationPath = parsed.customDerivationPath,
                                    network = network,
                                    isWatchOnly = parsed.isWatchOnly,
                                    seedFormat = seedFormat,
                                    gapLimit = gapLimit,
                                    masterFingerprint = fingerprint,
                                )
                                repository.importWallet(config)
                                walletsImported++

                                val newWalletId = repository.getActiveWalletId()
                                if (newWalletId != null) {
                                    restoreWalletSettings(newWalletId, entry)
                                    if (importLabels) {
                                        restoreLabelsForWallet(newWalletId, entry)
                                    }
                                }
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) {
                                    android.util.Log.w("WalletViewModel", "Skip wallet import: ${e.message}")
                                }
                                walletsSkipped++
                            }
                        }
                    }
                }

            if (importServers) {
                val serverSettingsObj = backupJson.optJSONObject("serverSettings")
                    ?: JSONObject().apply {
                        backupJson.optJSONArray("electrumServers")?.let { put("electrumServers", it) }
                    }
                if (serverSettingsObj.length() > 0) {
                    restoreServerSettings(serverSettingsObj)
                }
                restoreLiquidServers(backupJson.optJSONArray("liquidServers"))
            }

            if (importAppSettings) {
                val settingsObj = backupJson.optJSONObject("appSettings")
                if (settingsObj != null) {
                    restoreAppSettings(settingsObj)
                }
            }

            _uiState.value = _uiState.value.copy(isLoading = false)

            val msg = buildString {
                append("Restored successfully.")
                if (walletsImported > 0) append(" $walletsImported wallet(s) imported.")
                if (walletsSkipped > 0) append(" $walletsSkipped skipped (already exist).")
            }
            _fullBackupResultMessage.value = msg
            _events.emit(WalletEvent.WalletImported)
            true
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            _fullBackupResultMessage.value = "Restore failed: ${e.message}"
            _events.emit(WalletEvent.Error("Restore failed: ${e.message}"))
            false
        }
    }

    private fun findWalletIdByMnemonic(mnemonic: String): String? {
        return repository.getAllWalletIds().firstOrNull { id ->
            repository.getKeyMaterial(id)?.mnemonic == mnemonic
        }
    }

    private fun restoreLabelsForWallet(walletId: String, walletEntry: JSONObject) {
        val labelsObj = walletEntry.optJSONObject("labels") ?: return
        val addrLabels = labelsObj.optJSONObject("addresses")
        val txLabels = labelsObj.optJSONObject("transactions")
        val liquidTxLabels = labelsObj.optJSONObject("liquidTransactions")

        addrLabels?.keys()?.forEach { addr ->
            val label = addrLabels.optString(addr, "")
            if (label.isNotBlank()) {
                repository.saveAddressLabelForWallet(walletId, addr, label)
            }
        }
        txLabels?.keys()?.forEach { txid ->
            val label = txLabels.optString(txid, "")
            if (label.isNotBlank()) {
                repository.saveTransactionLabelForWallet(walletId, txid, label)
            }
        }
        liquidTxLabels?.keys()?.forEach { txid ->
            val label = liquidTxLabels.optString(txid, "")
            if (label.isNotBlank()) {
                repository.saveLiquidTransactionLabelForWallet(walletId, txid, label)
            }
        }

        val metadataObj = walletEntry.optJSONObject("liquidMetadata")
        if (metadataObj != null) {
            val sourcesObj = metadataObj.optJSONObject("transactionSources")
            sourcesObj?.keys()?.forEach { txid ->
                val sourceName = sourcesObj.optString(txid, "")
                val source = try { LiquidTxSource.valueOf(sourceName) } catch (_: Exception) { null }
                if (source != null) {
                    repository.saveLiquidTransactionSourceForWallet(walletId, txid, source)
                }
            }
            val detailsObj = metadataObj.optJSONObject("swapDetails")
            detailsObj?.keys()?.forEach { txid ->
                val d = detailsObj.optJSONObject(txid) ?: return@forEach
                try {
                    repository.saveLiquidSwapDetailsForWallet(walletId, txid, LiquidSwapDetails(
                        service = SwapService.valueOf(d.getString("service")),
                        direction = SwapDirection.valueOf(d.getString("direction")),
                        swapId = d.getString("swapId"),
                        role = LiquidSwapTxRole.valueOf(d.getString("role")),
                        depositAddress = d.optString("depositAddress"),
                        receiveAddress = d.optString("receiveAddress"),
                        refundAddress = d.optString("refundAddress"),
                        sendAmountSats = d.optLong("sendAmountSats"),
                        expectedReceiveAmountSats = d.optLong("expectedReceiveAmountSats"),
                    ))
                } catch (_: Exception) {}
            }
        }
    }

    private fun restoreLiquidServers(serversArray: org.json.JSONArray?) {
        if (serversArray == null || serversArray.length() == 0) return
        val existing = repository.getAllLiquidServers()
        val existingKeys = existing.map { "${it.cleanUrl()}:${it.port}" }.toSet()
        val activeLiquidId = repository.getActiveLiquidServerId()

        for (i in 0 until serversArray.length()) {
            val obj = serversArray.getJSONObject(i)
            val url = obj.getString("url")
            val port = obj.optInt("port", 995)
            val key = "${github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig(url = url, port = port).cleanUrl()}:$port"

            if (key in existingKeys) continue

            val config = github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig(
                name = obj.optString("name", ""),
                url = url,
                port = port,
                useSsl = obj.optBoolean("useSsl", true),
                useTor = obj.optBoolean("useTor", false),
            )
            repository.saveLiquidServer(config)
            if (obj.optBoolean("isActive", false) && activeLiquidId == null) {
                repository.setActiveLiquidServerId(config.id)
            }
        }
    }

    private fun restoreAppSettings(settings: JSONObject) {
        settings.optString("layer1Denomination", "").takeIf { it.isNotBlank() }
            ?.let { repository.setLayer1Denomination(it) }
        settings.optString("layer2Denomination", "").takeIf { it.isNotBlank() }
            ?.let { repository.setLayer2Denomination(it) }
        if (settings.has("autoSwitchServer")) {
            repository.setAutoSwitchServerEnabled(settings.getBoolean("autoSwitchServer"))
        }
        if (settings.has("torEnabled")) repository.setTorEnabled(settings.getBoolean("torEnabled"))
        if (settings.has("spendUnconfirmed")) repository.setSpendUnconfirmed(settings.getBoolean("spendUnconfirmed"))
        if (settings.has("nfcEnabled")) repository.setNfcEnabled(settings.getBoolean("nfcEnabled"))
        if (settings.has("privacyMode")) repository.setPrivacyMode(settings.getBoolean("privacyMode"))
        if (settings.has("walletNotificationsEnabled")) repository.setWalletNotificationsEnabled(settings.getBoolean("walletNotificationsEnabled"))
        settings.optString("mempoolServer", "").takeIf { it.isNotBlank() }
            ?.let { repository.setMempoolServer(it) }
        settings.optString("mempoolCustomUrl", "").takeIf { it.isNotBlank() }
            ?.let { repository.setCustomMempoolUrl(it) }
        settings.optString("feeSource", "").takeIf { it.isNotBlank() }
            ?.let { repository.setFeeSource(it) }
        settings.optString("feeSourceCustomUrl", "").takeIf { it.isNotBlank() }
            ?.let { repository.setCustomFeeSourceUrl(it) }
        settings.optString("priceSource", "").takeIf { it.isNotBlank() }
            ?.let { repository.setPriceSource(it) }
        if (settings.has("layer2Enabled")) repository.setLayer2Enabled(settings.getBoolean("layer2Enabled"))
        if (settings.has("liquidTorEnabled")) {
            repository.setLiquidTorEnabled(settings.getBoolean("liquidTorEnabled"))
        }
        settings.optString("boltzApiSource", "").takeIf { it.isNotBlank() }
            ?.let { repository.setBoltzApiSource(it) }
        settings.optString("sideSwapApiSource", "").takeIf { it.isNotBlank() }
            ?.let { repository.setSideSwapApiSource(it) }
        settings.optString("preferredSwapService", "").takeIf { it.isNotBlank() }?.let { name ->
            try { repository.setPreferredSwapService(SwapService.valueOf(name)) } catch (_: Exception) {}
        }
        if (settings.has("liquidAutoSwitch")) {
            repository.setLiquidAutoSwitchEnabled(settings.getBoolean("liquidAutoSwitch"))
        }
        if (settings.has("liquidServerSelectedByUser")) {
            repository.setUserSelectedLiquidServer(settings.getBoolean("liquidServerSelectedByUser"))
        }
        settings.optString("liquidExplorer", "").takeIf { it.isNotBlank() }
            ?.let { repository.setLiquidExplorer(it) }
        settings.optString("liquidExplorerCustomUrl", "").takeIf { it.isNotBlank() }
            ?.let { repository.setCustomLiquidExplorerUrl(it) }
    }

    private fun restoreWalletSettings(walletId: String, walletEntry: JSONObject) {
        val settingsObj = walletEntry.optJSONObject("walletSettings") ?: return

        if (settingsObj.has("liquidEnabled")) {
            repository.setLiquidEnabledForWallet(walletId, settingsObj.getBoolean("liquidEnabled"))
        }
        val frozenArr = settingsObj.optJSONArray("frozenUtxos")
        if (frozenArr != null && frozenArr.length() > 0) {
            val outpoints = (0 until frozenArr.length()).map { frozenArr.getString(it) }.toSet()
            repository.setFrozenUtxosForWallet(walletId, outpoints)
        }

        val walletObj = walletEntry.optJSONObject("wallet")
        if (walletObj != null && walletObj.optBoolean("isLocked", false)) {
            repository.setWalletLocked(walletId, true)
        }
    }

    /**
     * Parse a backup file and return the parsed data for preview/import
     * Returns a Pair of (JSONObject payload, Boolean wasEncrypted)
     */
    suspend fun parseBackupFile(
        uri: Uri,
        password: String?,
    ): JSONObject {
        return withContext(Dispatchers.IO) {
            val rawBytes =
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: throw IllegalStateException("Could not read file")

            val fileJson = JSONObject(String(rawBytes, Charsets.UTF_8))

            if (fileJson.optBoolean("encrypted", false)) {
                if (password.isNullOrEmpty()) {
                    throw IllegalStateException("This backup is encrypted. Please enter the password.")
                }
                val salt = Base64.decode(fileJson.getString("salt"), Base64.NO_WRAP)
                val iv = Base64.decode(fileJson.getString("iv"), Base64.NO_WRAP)
                val ciphertext = Base64.decode(fileJson.getString("data"), Base64.NO_WRAP)

                val plaintext = decryptData(EncryptedPayload(salt, iv, ciphertext), password)
                JSONObject(String(plaintext, Charsets.UTF_8))
            } else {
                fileJson
            }
        }
    }

    /**
     * Import a wallet from a parsed backup JSON object
     */
    fun importFromBackup(
        backupJson: JSONObject,
        importServerSettings: Boolean = true,
    ) {
        val walletObj = backupJson.getJSONObject("wallet")
        val keyMaterialObj = backupJson.getJSONObject("keyMaterial")

        // Delegate JSON field extraction to testable pure function
        val parsed =
            try {
                BitcoinUtils.parseBackupJson(walletObj, keyMaterialObj)
            } catch (e: Exception) {
                val message = e.message ?: "Failed to import backup"
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(error = message)
                    _events.emit(WalletEvent.Error(message))
                }
                return
            }

        val network =
            try {
                BitcoinUtils.parseSupportedWalletNetwork(parsed.network)
            } catch (e: Exception) {
                viewModelScope.launch {
                    val message = e.message ?: "Failed to import backup"
                    _uiState.value = _uiState.value.copy(error = message)
                    _events.emit(WalletEvent.Error(message))
                }
                return
            }

        val seedFormat =
            try {
                SeedFormat.valueOf(parsed.seedFormat)
            } catch (_: Exception) {
                SeedFormat.BIP39
            }

        val config =
            WalletImportConfig(
                name = parsed.name,
                keyMaterial = parsed.keyMaterial,
                addressType = parsed.addressType,
                customDerivationPath = parsed.customDerivationPath,
                network = network,
                isWatchOnly = parsed.isWatchOnly,
                seedFormat = seedFormat,
            )

        // Import the wallet, then restore metadata once import completes
        val labelsObj = backupJson.optJSONObject("labels")
        val liquidMetadataObj = backupJson.optJSONObject("liquidMetadata")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Restore server settings before import so the imported wallet can use them immediately.
            if (importServerSettings) {
                val serverSettingsObj = backupJson.optJSONObject("serverSettings")
                if (serverSettingsObj != null) {
                    restoreServerSettings(serverSettingsObj)
                }
            }

            when (val result = repository.importWallet(config)) {
                is WalletResult.Success -> {
                    val restoredWalletId = repository.getActiveWalletId() ?: walletState.value.activeWallet?.id

                    if (restoredWalletId != null && (labelsObj != null || liquidMetadataObj != null)) {
                        restoreBackupMetadata(
                            walletId = restoredWalletId,
                            labelsObj = labelsObj,
                            liquidMetadataObj = liquidMetadataObj,
                        )
                    }

                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(WalletEvent.WalletImported)

                    if (labelsObj != null || liquidMetadataObj != null) {
                        _events.emit(WalletEvent.LabelsRestored)
                    }

                    if (_uiState.value.isConnected) {
                        sync()
                    }
                }
                is WalletResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    _events.emit(WalletEvent.Error(result.message))
                }
            }
        }
    }

    private fun restoreBackupMetadata(
        walletId: String,
        labelsObj: JSONObject?,
        liquidMetadataObj: JSONObject?,
    ) {
        val addressLabels = labelsObj?.optJSONObject("addresses")
        if (addressLabels != null) {
            val keys = addressLabels.keys()
            while (keys.hasNext()) {
                val addr = keys.next()
                val label = addressLabels.getString(addr)
                repository.saveAddressLabelForWallet(walletId, addr, label)
            }
        }

        val txLabels = labelsObj?.optJSONObject("transactions")
        if (txLabels != null) {
            val keys = txLabels.keys()
            while (keys.hasNext()) {
                val txid = keys.next()
                val label = txLabels.getString(txid)
                repository.saveTransactionLabelForWallet(walletId, txid, label)
            }
        }

        val liquidTxLabels = labelsObj?.optJSONObject("liquidTransactions")
        if (liquidTxLabels != null) {
            val keys = liquidTxLabels.keys()
            while (keys.hasNext()) {
                val txid = keys.next()
                val label = liquidTxLabels.getString(txid)
                repository.saveLiquidTransactionLabelForWallet(walletId, txid, label)
            }
        }

        val liquidTxSources = liquidMetadataObj?.optJSONObject("transactionSources")
        if (liquidTxSources != null) {
            val keys = liquidTxSources.keys()
            while (keys.hasNext()) {
                val txid = keys.next()
                val source =
                    runCatching {
                        LiquidTxSource.valueOf(liquidTxSources.getString(txid))
                    }.getOrNull() ?: continue
                repository.saveLiquidTransactionSourceForWallet(walletId, txid, source)
            }
        }

        val liquidSwapDetails = liquidMetadataObj?.optJSONObject("swapDetails")
        if (liquidSwapDetails != null) {
            val keys = liquidSwapDetails.keys()
            while (keys.hasNext()) {
                val txid = keys.next()
                val detailsJson = liquidSwapDetails.optJSONObject(txid) ?: continue
                val details =
                    runCatching {
                        LiquidSwapDetails(
                            service = SwapService.valueOf(detailsJson.getString("service")),
                            direction = SwapDirection.valueOf(detailsJson.getString("direction")),
                            swapId = detailsJson.getString("swapId"),
                            role = LiquidSwapTxRole.valueOf(
                                detailsJson.optString("role", LiquidSwapTxRole.FUNDING.name),
                            ),
                            depositAddress = detailsJson.getString("depositAddress"),
                            receiveAddress = detailsJson.optString("receiveAddress").takeIf { it.isNotBlank() },
                            refundAddress = detailsJson.optString("refundAddress").takeIf { it.isNotBlank() },
                            sendAmountSats = detailsJson.optLong("sendAmountSats", 0L),
                            expectedReceiveAmountSats = detailsJson.optLong("expectedReceiveAmountSats", 0L),
                        )
                    }.getOrNull() ?: continue
                repository.saveLiquidSwapDetailsForWallet(walletId, txid, details)
            }
        }
    }

    /**
     * Restore server settings from a backup JSON object.
     * Merges Electrum servers (skips duplicates by url+port), restores block explorer
     * and fee source settings.
     */
    private fun restoreServerSettings(serverSettingsObj: JSONObject) {
        try {
            // Restore Electrum servers (merge, skip duplicates)
            val serversArray = serverSettingsObj.optJSONArray("electrumServers")
            if (serversArray != null) {
                val existingServers = repository.getAllElectrumServers()
                val existingKeys = existingServers.map { "${it.cleanUrl()}:${it.port}" }.toSet()
                var restoredActiveId: String? = null

                for (i in 0 until serversArray.length()) {
                    val serverObj = serversArray.getJSONObject(i)
                    val url = serverObj.getString("url")
                    val port = serverObj.optInt("port", 50001)
                    val key = "${ElectrumConfig(url = url).cleanUrl()}:$port"

                    if (key in existingKeys) {
                        // Server already exists; if it was marked active, find the existing one
                        if (serverObj.optBoolean("isActive", false)) {
                            restoredActiveId = existingServers.find {
                                "${it.cleanUrl()}:${it.port}" == key
                            }?.id
                        }
                        continue
                    }

                    val newConfig = ElectrumConfig(
                        name = serverObj.optString("name", "").ifBlank { null },
                        url = url,
                        port = port,
                        useSsl = serverObj.optBoolean("useSsl", false),
                        useTor = serverObj.optBoolean("useTor", false),
                    )
                    val saved = repository.saveElectrumServer(newConfig)
                    if (serverObj.optBoolean("isActive", false)) {
                        restoredActiveId = saved.id
                    }
                }

                // Refresh the servers state flow
                _serversState.value = ServersState(
                    servers = repository.getAllElectrumServers(),
                    activeServerId = restoredActiveId ?: repository.getActiveServerId(),
                )
            }

            // Restore block explorer custom URL only — don't restore the type
            // selection so we never silently enable an external service on import.
            val explorerObj = serverSettingsObj.optJSONObject("blockExplorer")
            if (explorerObj != null) {
                val customUrl = explorerObj.optString("customUrl", "")
                if (customUrl.isNotBlank()) {
                    repository.setCustomMempoolUrl(customUrl)
                }
            }

            // Restore fee source custom URL only — same rationale as above.
            val feeObj = serverSettingsObj.optJSONObject("feeSource")
            if (feeObj != null) {
                val customUrl = feeObj.optString("customUrl", "")
                if (customUrl.isNotBlank()) {
                    repository.setCustomFeeSourceUrl(customUrl)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w("WalletViewModel", "Failed to restore server settings: ${e.message}")
            }
        }
    }

    // ==================== Encryption Helpers ====================
    // Delegates to CryptoUtils for testability.

    private data class EncryptedPayload(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedPayload) return false
            return salt.contentEquals(other.salt) &&
                iv.contentEquals(other.iv) &&
                ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int {
            var result = salt.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }

        fun toCryptoPayload() = CryptoUtils.EncryptedPayload(salt, iv, ciphertext)
    }

    private fun encryptData(
        plaintext: ByteArray,
        password: String,
    ): EncryptedPayload {
        val result = CryptoUtils.encrypt(plaintext, password, PBKDF2_ITERATIONS)
        return EncryptedPayload(result.salt, result.iv, result.ciphertext)
    }

    private fun decryptData(
        payload: EncryptedPayload,
        password: String,
    ): ByteArray {
        return CryptoUtils.decrypt(payload.toCryptoPayload(), password, PBKDF2_ITERATIONS)
    }

    // ==================== Auto-Switch Server ====================

    /**
     * Set auto-switch server on disconnect
     */
    fun setAutoSwitchServer(enabled: Boolean) {
        repository.setAutoSwitchServerEnabled(enabled)
        _autoSwitchServer.value = enabled
    }

    // ==================== Tor Management ====================

    /**
     * Check if Tor is enabled in settings
     */
    fun isTorEnabled(): Boolean = repository.isTorEnabled()

    /**
     * Set Tor enabled state
     */
    fun setTorEnabled(enabled: Boolean) {
        repository.setTorEnabled(enabled)
        if (enabled) {
            startTor()
        } else {
            // Disabling Tor: disconnect from server since the connection
            // is now broken (Tor proxy is gone). The user can reconnect
            // if the server is reachable without Tor.
            if (_uiState.value.isConnected || _uiState.value.isConnecting) {
                disconnect()
            }
            stopTor()
        }
    }

    /**
     * Start the Tor service
     */
    fun startTor() {
        torManager.start()
    }

    /**
     * Stop the Tor service
     */
    fun stopTor() {
        torManager.stop()
    }

    /**
     * Check if Tor is ready for use
     */
    fun isTorReady(): Boolean = torManager.isReady()

    /**
     * Accept a server's certificate after user approval (TOFU).
     * Stores the fingerprint and retries the connection.
     */
    fun acceptCertificate() {
        val state = _certDialogState.value ?: return
        val certInfo = state.certInfo

        // Store the approved fingerprint
        repository.acceptServerCertificate(certInfo.host, certInfo.port, certInfo.sha256Fingerprint)

        // Clear dialog
        _certDialogState.value = null

        // Retry the connection
        if (state.pendingConfig != null) {
            connectToElectrum(state.pendingConfig)
        } else if (state.pendingServerId != null) {
            connectToServer(state.pendingServerId)
        }
    }

    /**
     * Reject a server's certificate - cancel the connection attempt.
     */
    fun rejectCertificate() {
        _certDialogState.value = null
        _uiState.value = _uiState.value.copy(isConnecting = false, error = null)
    }

    /**
     * Clean up resources when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        lifecycleCoordinator.dispose()
        // Run on IO to avoid blocking the main thread with socket closes
        CoroutineScope(Dispatchers.IO).launch {
            repository.disconnect()
            repository.close()
            torManager.stop()
        }
    }

    /**
     * Disconnect from the current Electrum server
     */
    fun disconnect() {
        if (_uiState.value.isConnecting) {
            cancelConnection()
            return
        }
        stopBackgroundSync()
        stopHeartbeat()
        _uiState.value = _uiState.value.copy(isConnected = false, serverVersion = null)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setUserDisconnected(true)
            repository.disconnect()
        }
    }

    /**
     * Cancel an in-progress connection attempt
     */
    fun cancelConnection() {
        stopBackgroundSync()
        stopHeartbeat()
        val jobToCancel = connectionJob
        connectionJob = null
        _uiState.value = _uiState.value.copy(isConnecting = false, isConnected = false, serverVersion = null, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setUserDisconnected(true)
            jobToCancel?.cancelAndJoin()
            repository.disconnect()
        }
    }

    // ==================== Display Settings ====================

    /**
     * Toggle privacy mode (hides all monetary amounts)
     */
    fun togglePrivacyMode() {
        val newValue = !_privacyMode.value
        _privacyMode.value = newValue
        repository.setPrivacyMode(newValue)
    }

    /**
     * Set the Layer 1 denomination preference
     */
    fun setDenomination(denomination: String) {
        repository.setLayer1Denomination(denomination)
        _denominationState.value = denomination
    }

    fun setSwipeMode(mode: String) {
        repository.setSwipeMode(mode)
        _swipeMode.value = mode
    }

    // ==================== Mempool Server Settings ====================

    /**
     * Get the current mempool server setting
     */
    fun getMempoolServer(): String = repository.getMempoolServer()

    /**
     * Set the mempool server preference
     */
    fun setMempoolServer(server: String) {
        repository.setMempoolServer(server)
        _mempoolServerState.value = server
    }

    /**
     * Get the full mempool URL for block explorer
     */
    fun getMempoolUrl(): String = repository.getMempoolUrl()

    /**
     * Get the custom mempool URL
     */
    fun getCustomMempoolUrl(): String {
        return repository.getCustomMempoolUrl() ?: ""
    }

    /**
     * Set the custom mempool URL
     */
    fun setCustomMempoolUrl(url: String) {
        repository.setCustomMempoolUrl(url)
    }

    /**
     * Get the custom fee source server URL
     */
    fun getCustomFeeSourceUrl(): String {
        return repository.getCustomFeeSourceUrl() ?: ""
    }

    /**
     * Check if the currently active fee source URL is a .onion address.
     * Returns false if fee source is off, Electrum, or a clearnet URL.
     */
    fun isFeeSourceOnion(): Boolean {
        return repository.getFeeSourceUrl()?.let { url ->
            try {
                java.net.URI(url).host?.endsWith(".onion") == true
            } catch (_: Exception) {
                url.endsWith(".onion")
            }
        } == true
    }

    /**
     * Check if the currently active price source is a .onion address.
     */
    fun isPriceSourceOnion(): Boolean {
        return repository.getPriceSource() == SecureStorage.PRICE_SOURCE_MEMPOOL_ONION
    }

    /**
     * Set the custom fee source server URL
     */
    fun setCustomFeeSourceUrl(url: String) {
        repository.setCustomFeeSourceUrl(url)
    }

    // ==================== Spend Unconfirmed ====================

    /**
     * Get whether spending unconfirmed UTXOs is allowed
     */
    fun getSpendUnconfirmed(): Boolean = repository.getSpendUnconfirmed()

    /**
     * Set whether spending unconfirmed UTXOs is allowed
     */
    fun setSpendUnconfirmed(enabled: Boolean) {
        repository.setSpendUnconfirmed(enabled)
    }

    fun isNfcEnabled(): Boolean = repository.isNfcEnabled()

    fun setNfcEnabled(enabled: Boolean) {
        repository.setNfcEnabled(enabled)
    }

    fun isWalletNotificationsEnabled(): Boolean = repository.isWalletNotificationsEnabled()

    fun setWalletNotificationsEnabled(enabled: Boolean) {
        repository.setWalletNotificationsEnabled(enabled)
    }

    // ==================== Fee Estimation Settings ====================

    /**
     * Set the fee source preference
     */
    fun setFeeSource(source: String) {
        repository.setFeeSource(source)
        _feeSourceState.value = source

        if (source == SecureStorage.FEE_SOURCE_OFF) {
            _feeEstimationState.value = FeeEstimationResult.Disabled
        } else {
            // Immediately fetch when enabling a fee source
            fetchFeeEstimates()
        }
    }

    /**
     * Fetch fee estimates from the configured source
     * Call this when opening the Send screen
     * Uses precise endpoint only if the connected Electrum server supports sub-sat fees
     */
    fun fetchFeeEstimates() {
        val feeSource = repository.getFeeSource()

        if (feeSource == SecureStorage.FEE_SOURCE_OFF) {
            _feeEstimationState.value = FeeEstimationResult.Disabled
            return
        }

        viewModelScope.launch {
            _feeEstimationState.value = FeeEstimationResult.Loading

            if (feeSource == SecureStorage.FEE_SOURCE_ELECTRUM) {
                // Fetch from connected Electrum server via BDK's estimateFee()
                val result = repository.fetchElectrumFeeEstimates()
                _feeEstimationState.value =
                    result.fold(
                        onSuccess = { estimates -> FeeEstimationResult.Success(estimates) },
                        onFailure = { error -> FeeEstimationResult.Error(error.message ?: "Not connected to server") },
                    )
                return@launch
            }

            // Fetch from mempool.space HTTP API
            val feeSourceUrl =
                repository.getFeeSourceUrl() ?: run {
                    _feeEstimationState.value = FeeEstimationResult.Disabled
                    return@launch
                }

            val useTorProxy =
                try {
                    java.net.URI(feeSourceUrl).host?.endsWith(".onion") == true
                } catch (_: Exception) {
                    feeSourceUrl.endsWith(".onion")
                }

            // If Tor is required, ensure it's running — fee source manages its own Tor needs
            if (useTorProxy) {
                if (!torManager.isReady()) {
                    torManager.start()
                    if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                        val msg = if (torState.value.status == TorStatus.ERROR) {
                            "Tor failed to start"
                        } else {
                            "Tor connection timed out"
                        }
                        _feeEstimationState.value = FeeEstimationResult.Error(msg)
                        return@launch
                    }
                    // Brief settle time for the SOCKS proxy after bootstrap
                    delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                }
            }

            // HTTP fee providers should decide whether `/precise` works; the service
            // already falls back to `/recommended` when the endpoint is unsupported.
            val usePrecise = true

            val result = feeEstimationService.fetchFeeEstimates(feeSourceUrl, useTorProxy, usePrecise)

            _feeEstimationState.value =
                result.fold(
                    onSuccess = { estimates -> FeeEstimationResult.Success(estimates) },
                    onFailure = { error -> FeeEstimationResult.Error(error.message ?: "Unknown error") },
                )
        }
    }

    // ==================== BTC/USD Price ====================

    /**
     * Set the BTC/USD price source preference
     */
    fun setPriceSource(source: String) {
        repository.setPriceSource(source)
        _priceSourceState.value = source

        // Clear price when disabled, fetch when enabled
        if (source == SecureStorage.PRICE_SOURCE_OFF) {
            _btcPriceState.value = null
        } else {
            fetchBtcPrice()
        }
    }

    fun reloadRestoredAppSettings() {
        _denominationState.value = repository.getLayer1Denomination()
        _mempoolServerState.value = repository.getMempoolServer()
        _feeSourceState.value = repository.getFeeSource()
        _priceSourceState.value = repository.getPriceSource()
        _privacyMode.value = repository.getPrivacyMode()
        _swipeMode.value = repository.getSwipeMode()
        _autoSwitchServer.value = repository.isAutoSwitchServerEnabled()

        if (_feeSourceState.value == SecureStorage.FEE_SOURCE_OFF) {
            _feeEstimationState.value = FeeEstimationResult.Disabled
        } else {
            fetchFeeEstimates()
        }

        if (_priceSourceState.value == SecureStorage.PRICE_SOURCE_OFF) {
            _btcPriceState.value = null
        } else {
            fetchBtcPrice()
        }

        val shouldKeepTorRunning =
            repository.isTorEnabled() ||
                isFeeSourceOnion() ||
                isPriceSourceOnion()
        if (shouldKeepTorRunning) {
            startTor()
        } else {
            stopTor()
        }

        _settingsRefreshVersion.value += 1
    }

    /**
     * Fetch BTC/USD price from the configured source
     */
    fun fetchBtcPrice() {
        val source = repository.getPriceSource()

        if (source == SecureStorage.PRICE_SOURCE_OFF) {
            _btcPriceState.value = null
            return
        }

        viewModelScope.launch {
            // If onion source, ensure Tor is running
            if (source == SecureStorage.PRICE_SOURCE_MEMPOOL_ONION) {
                if (!torManager.isReady()) {
                    torManager.start()
                    if (!torManager.awaitReady(TOR_BOOTSTRAP_TIMEOUT_MS)) {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.e("WalletViewModel", "Tor not ready for price fetch")
                        }
                        return@launch
                    }
                    // Brief settle time for the SOCKS proxy after bootstrap
                    delay(TOR_POST_BOOTSTRAP_DELAY_MS)
                }
            }

            val price =
                when (source) {
                    SecureStorage.PRICE_SOURCE_MEMPOOL -> btcPriceService.fetchFromMempool()
                    SecureStorage.PRICE_SOURCE_MEMPOOL_ONION -> btcPriceService.fetchFromMempoolOnion()
                    SecureStorage.PRICE_SOURCE_COINGECKO -> btcPriceService.fetchFromCoinGecko()
                    else -> null
                }
            _btcPriceState.value = price
        }
    }

    // ==================== Security Settings ====================

    /**
     * Get the current security method
     */
    fun getSecurityMethod(): SecureStorage.SecurityMethod = repository.getSecurityMethod()

    /**
     * Set the security method
     */
    fun setSecurityMethod(method: SecureStorage.SecurityMethod) {
        repository.setSecurityMethod(method)
    }

    /**
     * Save PIN code
     */
    fun savePin(pin: String) {
        repository.savePin(pin)
    }

    /**
     * Clear PIN code
     */
    fun clearPin() {
        repository.clearPin()
    }

    /**
     * Check if security is enabled
     */
    fun isSecurityEnabled(): Boolean = repository.isSecurityEnabled()

    /**
     * Get lock timing setting
     */
    fun getLockTiming(): SecureStorage.LockTiming = repository.getLockTiming()

    /**
     * Set lock timing setting
     */
    fun setLockTiming(timing: SecureStorage.LockTiming) {
        repository.setLockTiming(timing)
    }

    /**
     * Get whether screenshots are disabled
     */
    fun getDisableScreenshots(): Boolean = repository.getDisableScreenshots()

    /**
     * Set whether screenshots are disabled
     */
    fun setDisableScreenshots(disabled: Boolean) {
        repository.setDisableScreenshots(disabled)
    }

    // ==================== Duress PIN / Decoy Wallet ====================

    /**
     * Check if duress mode is enabled in settings
     */
    fun isDuressEnabled(): Boolean = repository.isDuressEnabled()

    /**
     * Set up a duress wallet with a PIN and import config.
     * Creates the decoy wallet, saves the duress PIN, and enables duress mode.
     */
    fun setupDuress(
        pin: String,
        config: WalletImportConfig,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            when (val result = repository.createDuressWallet(config)) {
                is WalletResult.Success -> {
                    repository.saveDuressPin(pin)
                    repository.setDuressEnabled(true)
                    onSuccess()
                }
                is WalletResult.Error -> {
                    onError(result.message)
                }
            }
        }
    }

    /**
     * Disable duress mode: delete the decoy wallet and clear all duress data
     */
    fun disableDuress(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isDuressMode.value = false
            repository.deleteDuressWallet()
            onComplete()
        }
    }

    /**
     * Enter duress mode: switch to the decoy wallet.
     * Called when the duress PIN is entered on the lock screen.
     */
    fun enterDuressMode() {
        viewModelScope.launch {
            val result = repository.switchToDuressWallet()
            if (result is WalletResult.Success) {
                _isDuressMode.value = true
                if (_uiState.value.isConnected) {
                    launchSubscriptions()
                }
            } else {
                // Duress wallet no longer exists — clean up stale config
                _isDuressMode.value = false
                repository.deleteDuressWallet()
            }
        }
    }

    /**
     * Exit duress mode: switch back to the real wallet.
     * Called when the real PIN or biometric is used on the lock screen.
     */
    fun exitDuressMode() {
        if (!_isDuressMode.value) return
        viewModelScope.launch {
            _isDuressMode.value = false
            repository.switchToRealWallet()
            if (_uiState.value.isConnected) {
                launchSubscriptions()
            }
        }
    }

    /**
     * Get the duress wallet ID (for filtering wallet lists)
     */
    fun getDuressWalletId(): String? = repository.getDuressWalletId()

    // ==================== Auto-Wipe ====================

    /**
     * Get the auto-wipe threshold setting
     */
    fun getAutoWipeThreshold(): SecureStorage.AutoWipeThreshold = repository.getAutoWipeThreshold()

    /**
     * Set the auto-wipe threshold
     */
    fun setAutoWipeThreshold(threshold: SecureStorage.AutoWipeThreshold) {
        repository.setAutoWipeThreshold(threshold)
    }

    // ==================== Cloak Mode ====================

    fun isCloakModeEnabled(): Boolean = repository.isCloakModeEnabled()

    fun enableCloakMode(code: String) {
        repository.setCloakCode(code)
        repository.setCloakModeEnabled(true)
        repository.setPendingIconAlias(SecureStorage.ALIAS_CALCULATOR)
    }

    fun disableCloakMode() {
        repository.clearCloakData()
        // clearCloakData already schedules the alias swap back to default
    }

    /**
     * Wipe all wallet data. Called when auto-wipe threshold is reached.
     * Clears clipboard, stops Tor and deletes its data, and asks the repository
     * to wipe wallet databases, preferences, and the Electrum cache database.
     */
    fun wipeAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val app = getApplication<Application>()

            // Clear clipboard to prevent sensitive data (addresses, PSBTs) from surviving wipe
            try {
                val clipboard =
                    app.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("", ""),
                    )
                }
            } catch (_: Exception) {
            }

            // Stop Tor and wipe its data directory (relay descriptors, circuit state, etc.)
            try {
                torManager.wipeTorData()
            } catch (_: Exception) {
            }

            // Wipe all wallet data (BDK/LWK databases, preferences, Electrum cache DB, in-memory state)
            repository.wipeAllData()

            onComplete()
        }
    }

    // ==================== Sweep Private Key ====================

    private val _sweepState = MutableStateFlow(SweepState())
    val sweepState: StateFlow<SweepState> = _sweepState.asStateFlow()

    fun scanWifBalances(wif: String) {
        viewModelScope.launch {
            _sweepState.value = SweepState(isScanning = true, scanProgress = "Scanning...")
            when (
                val result =
                    repository.scanWifBalances(wif) { progress ->
                        _sweepState.value = _sweepState.value.copy(scanProgress = progress)
                    }
            ) {
                is WalletResult.Success -> {
                    _sweepState.value =
                        _sweepState.value.copy(
                            isScanning = false,
                            scanResults = result.data,
                            scanProgress = null,
                        )
                }
                is WalletResult.Error -> {
                    _sweepState.value =
                        _sweepState.value.copy(
                            isScanning = false,
                            error = result.message,
                            scanProgress = null,
                        )
                }
            }
        }
    }

    fun sweepPrivateKey(
        wif: String,
        destination: String,
        feeRate: Double,
    ) {
        viewModelScope.launch {
            _sweepState.value = _sweepState.value.copy(isSweeping = true, sweepProgress = "Building transactions...")
            when (
                val result =
                    repository.sweepPrivateKey(wif, destination, feeRate) { progress ->
                        _sweepState.value = _sweepState.value.copy(sweepProgress = progress)
                    }
            ) {
                is WalletResult.Success -> {
                    _sweepState.value =
                        _sweepState.value.copy(
                            isSweeping = false,
                            sweepTxids = result.data,
                            sweepProgress = null,
                        )
                    for (txid in result.data) {
                        _events.emit(WalletEvent.TransactionSent(txid))
                    }
                    // Quick sync to update balance with the incoming swept funds
                    sync()
                }
                is WalletResult.Error -> {
                    _sweepState.value =
                        _sweepState.value.copy(
                            isSweeping = false,
                            error = result.message,
                            sweepProgress = null,
                        )
                }
            }
        }
    }

    fun resetSweepState() {
        _sweepState.value = SweepState()
    }

    fun isWifPrivateKey(input: String): Boolean = repository.isWifPrivateKey(input)

    companion object {
        private const val CONNECTION_TIMEOUT_CLEARNET_MS = 15_000L
        private const val CONNECTION_TIMEOUT_TOR_MS = 30_000L
        private const val TOR_BOOTSTRAP_TIMEOUT_MS = 60_000L
        private const val TOR_POST_BOOTSTRAP_DELAY_MS = 2_500L
        private const val PBKDF2_ITERATIONS = 600_000
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val HEARTBEAT_PING_TIMEOUT_MS = 10_000L
        private const val HEARTBEAT_MAX_FAILURES = 3
    }
}

/**
 * UI state for wallet operations
 */
data class WalletUiState(
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isSending: Boolean = false,
    val sendStatus: String? = null,
    val serverVersion: String? = null,
    val error: String? = null,
)

/**
 * One-time events from the wallet
 */
sealed class WalletEvent {
    data object WalletImported : WalletEvent()

    data object WalletDeleted : WalletEvent()

    data object WalletSwitched : WalletEvent()

    data object Connected : WalletEvent()

    data object SyncCompleted : WalletEvent()

    data object ServerDeleted : WalletEvent()

    data class AddressGenerated(val address: String) : WalletEvent()

    data class TransactionSent(val txid: String) : WalletEvent()

    data class FeeBumped(val newTxid: String) : WalletEvent()

    data class CpfpCreated(val childTxid: String) : WalletEvent()

    data class TransactionRedirected(val newTxid: String) : WalletEvent()

    data class PsbtCreated(val psbtBase64: String) : WalletEvent()

    data class WalletExported(val walletName: String) : WalletEvent()

    data object LabelsRestored : WalletEvent()

    data class Bip329LabelsExported(val count: Int) : WalletEvent()

    data class Bip329LabelsImported(val count: Int) : WalletEvent()

    data class Error(val message: String) : WalletEvent()
}

/**
 * State for sweep private key operations
 */
data class SweepState(
    val isScanning: Boolean = false,
    val isSweeping: Boolean = false,
    val scanResults: List<WalletRepository.SweepScanResult> = emptyList(),
    val sweepTxids: List<String> = emptyList(),
    val scanProgress: String? = null,
    val sweepProgress: String? = null,
    val error: String? = null,
) {
    val totalBalanceSats: ULong get() = scanResults.sumOf { it.balanceSats.toLong() }.toULong()
    val hasBalance: Boolean get() = scanResults.isNotEmpty()
    val isComplete: Boolean get() = sweepTxids.isNotEmpty()
}

/**
 * State for Electrum servers
 */
data class ServersState(
    val servers: List<ElectrumConfig> = emptyList(),
    val activeServerId: String? = null,
)

/**
 * Draft state for Send screen (persisted while app is open/minimized)
 */
data class SendScreenDraft(
    val recipientAddress: String = "",
    val amountInput: String = "",
    val label: String = "",
    val feeRate: Double = 1.0,
    val isMaxSend: Boolean = false,
    val selectedUtxoOutpoints: List<String> = emptyList(), // Store outpoints to restore selection
)

/**
 * State for PSBT creation and signing flow (watch-only wallets)
 */
data class PsbtState(
    val isCreating: Boolean = false,
    val isBroadcasting: Boolean = false,
    val broadcastStatus: String? = null,
    val unsignedPsbtBase64: String? = null,
    val signedData: String? = null, // Signed PSBT/tx data awaiting broadcast confirmation
    val pendingLabel: String? = null,
    val error: String? = null,
    // Actual transaction details from BDK (not client-side estimates)
    val actualFeeSats: ULong = 0UL,
    val recipientAddress: String? = null,
    val recipientAmountSats: ULong = 0UL,
    val changeAmountSats: ULong? = null,
    val totalInputSats: ULong = 0UL,
)

/**
 * State for manual transaction broadcast (standalone, not tied to any wallet).
 */
data class ManualBroadcastState(
    val isBroadcasting: Boolean = false,
    val broadcastStatus: String? = null,
    val txid: String? = null,
    val error: String? = null,
)

/**
 * State for the certificate approval/warning dialog (TOFU)
 */
data class CertDialogState(
    val certInfo: CertificateInfo,
    val isFirstUse: Boolean, // true = first connection, false = cert changed
    val oldFingerprint: String? = null, // non-null when cert changed
    val pendingConfig: ElectrumConfig? = null, // config to retry after approval
    val pendingServerId: String? = null, // server ID to retry after approval
)

/**
 * Walk an exception's cause chain to find a specific exception type.
 * SSLSocket.startHandshake() wraps TrustManager exceptions in SSLHandshakeException,
 * so we need to unwrap to find our TOFU exceptions (CertificateFirstUseException, etc.).
 */
private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
