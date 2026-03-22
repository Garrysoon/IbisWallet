@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.ui.components.FeeRateSection
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.MAX_FEE_RATE_SAT_VB
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.StatusBadge
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrangeLight
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.matchesTimestampSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Method for speeding up a transaction
 */
enum class SpeedUpMethod {
    RBF,
    CPFP,
    REDIRECT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceScreen(
    walletState: WalletState = WalletState(),
    denomination: String = SecureStorage.DENOMINATION_BTC,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_DISABLED,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    onTogglePrivacy: () -> Unit = {},
    addressLabels: Map<String, String> = emptyMap(),
    transactionLabels: Map<String, String> = emptyMap(),
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    canBumpFee: (String) -> Boolean = { false },
    canCpfp: (String) -> Boolean = { false },
    onBumpFee: (String, Double) -> Unit = { _, _ -> },
    onCpfp: (String, Double) -> Unit = { _, _ -> },
    onRedirectTransaction: (String, Double) -> Unit = { _, _ -> },
    onSaveTransactionLabel: (String, String) -> Unit = { _, _ -> },
    onFetchTxVsize: suspend (String) -> Double? = { null },
    onRefreshFees: () -> Unit = {},
    onSync: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
    onManageWallets: () -> Unit = {},
    onScanQrResult: (String) -> Unit = {},
) {
    // State for selected transaction dialog
    var selectedTransaction by remember { mutableStateOf<TransactionDetails?>(null) }

    // QR scanner state
    var showQrScanner by remember { mutableStateOf(false) }

    // Quick receive dialog state
    var showQuickReceive by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Transaction display limit (progressive loading)
    var displayLimit by remember { mutableIntStateOf(25) }

    val useSats = denomination == SecureStorage.DENOMINATION_SATS

    // NFC reader mode: tapping an NFC tag with a bitcoin address or URI
    // routes through pendingSendInput -> Send screen.
    val context = LocalContext.current
    val nfcAvailable = context.getNfcAvailability().canRead
    DisposableEffect(nfcAvailable) {
        if (nfcAvailable) {
            (context as? MainActivity)?.enableNfcReaderMode()
        }
        onDispose {
            (context as? MainActivity)?.disableNfcReaderMode()
        }
    }

    // Show transaction detail dialog when a transaction is selected
    selectedTransaction?.let { tx ->
        val txCanRbf = canBumpFee(tx.txid)
        val txCanCpfp = canCpfp(tx.txid)
        val txLabel = transactionLabels[tx.txid] ?: tx.address?.let { addressLabels[it] }

        TransactionDetailDialog(
            transaction = tx,
            useSats = useSats,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            label = txLabel,
            canRbf = txCanRbf,
            canCpfp = txCanCpfp,
            availableBalance = walletState.balanceSats,
            feeEstimationState = feeEstimationState,
            minFeeRate = minFeeRate,
            onFetchVsize = onFetchTxVsize,
            onRefreshFees = onRefreshFees,
            onSpeedUp = { method, feeRate ->
                when (method) {
                    SpeedUpMethod.RBF -> onBumpFee(tx.txid, feeRate)
                    SpeedUpMethod.CPFP -> onCpfp(tx.txid, feeRate)
                    SpeedUpMethod.REDIRECT -> onRedirectTransaction(tx.txid, feeRate)
                }
            },
            onSaveLabel = { label -> onSaveTransactionLabel(tx.txid, label) },
            onDismiss = { selectedTransaction = null },
        )
    }

    // Track pull-to-refresh separately from sync state so background/auto syncs
    // don't cause the page to stretch — only user-initiated pull gestures do.
    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // Clear pull-refreshing state when sync finishes
    LaunchedEffect(walletState.isSyncing) {
        if (!walletState.isSyncing) {
            isPullRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = {
            if (walletState.isInitialized) {
                isPullRefreshing = true
                onSync()
            }
        },
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        indicator = {},
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        // Add stretch effect only when user is actively pulling
                        val progress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                        translationY = progress * 40f
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                val cardAccentColor = BitcoinOrange

                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = DarkCard,
                        ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                    ) {
                        // Top row — privacy toggle (left) + sync button (right)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .padding(bottom = 4.dp)
                                    .align(Alignment.TopCenter),
                        ) {
                            // Privacy toggle — pinned to the left
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .align(Alignment.CenterStart)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .clickable { onTogglePrivacy() },
                            ) {
                                Icon(
                                    imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle privacy",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Sync button — pinned to the right
                            val isSyncing = walletState.isSyncing
                            val syncEnabled = walletState.isInitialized && !walletState.isSyncing
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .align(Alignment.CenterEnd)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .clickable(enabled = syncEnabled) {
                                            onSync()
                                        },
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = cardAccentColor,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync",
                                        tint =
                                            if (walletState.isInitialized) {
                                                TextSecondary
                                            } else {
                                                TextSecondary.copy(
                                                    alpha = 0.3f,
                                                )
                                            },
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        // Balance content — vertically centered in the card
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Main balance with inline denomination — tap to toggle BTC/sats
                            Text(
                                text =
                                    if (privacyMode) {
                                        HIDDEN_AMOUNT
                                    } else if (useSats) {
                                        "${formatAmount(walletState.balanceSats, true)} sats"
                                    } else {
                                        "\u20BF ${formatAmount(walletState.balanceSats, false)}"
                                    },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onToggleDenomination,
                                ),
                            )

                            // USD value
                            if (btcPrice != null && btcPrice > 0) {
                                val usdValue = (walletState.balanceSats.toDouble() / 100_000_000.0) * btcPrice
                                Text(
                                    text = if (privacyMode) HIDDEN_AMOUNT else formatUsd(usdValue),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary,
                                )
                            }

                        }

                        // Quick receive button — pinned to bottom left
                        val quickReceiveEnabled = walletState.currentAddress != null
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable(enabled = quickReceiveEnabled) {
                                        showQuickReceive = true
                                    },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Quick receive",
                                tint = cardAccentColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // QR scan button — pinned to bottom right
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable(enabled = walletState.isInitialized) {
                                        showQrScanner = true
                                    },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = cardAccentColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))

                // Import wallet prompt if not initialized
                if (!walletState.isInitialized) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = DarkCard,
                            ),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No Wallet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add a wallet to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onManageWallets,
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, BitcoinOrangeLight),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = BitcoinOrange,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Wallet")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Recent Transactions Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .clickable {
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) searchQuery = ""
                                },
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isSearchActive) "Close search" else "Search",
                            tint = if (isSearchActive) {
                                BitcoinOrange
                            } else {
                                TextSecondary
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Search field
                if (isSearchActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Search labels, addresses, txid, or date...",
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = BitcoinOrange,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Filter transactions based on search query (always searches ALL transactions)
            // NOTE: These computations are in LazyListScope (non-composable),
            // so we use plain Kotlin instead of remember/derivedStateOf.
            val filteredTransactions =
                if (searchQuery.isBlank()) {
                    walletState.transactions
                } else {
                    val query = searchQuery.lowercase()
                    walletState.transactions.filter { tx ->
                        val txLabel = transactionLabels[tx.txid]
                        val addrLabel = tx.address?.let { addressLabels[it] }
                        val address = tx.address ?: ""

                        tx.txid.lowercase().contains(query) ||
                            txLabel?.lowercase()?.contains(query) == true ||
                            addrLabel?.lowercase()?.contains(query) == true ||
                            address.lowercase().contains(query) ||
                            matchesTimestampSearch(tx.timestamp, searchQuery)
                    }
                }

            // When searching, show all results; otherwise apply display limit
            val isSearching = searchQuery.isNotBlank()
            val visibleTransactions =
                if (isSearching) {
                    filteredTransactions
                } else {
                    filteredTransactions.take(displayLimit)
                }
            val totalCount = filteredTransactions.size
            val visibleCount = visibleTransactions.size
            val hasMore = !isSearching && visibleCount < totalCount

            if (filteredTransactions.isEmpty()) {
                item {
                    // Empty state for transactions
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = DarkCard,
                            ),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching transactions" else "No transactions yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (searchQuery.isNotBlank()) {
                                            "Try a different search term"
                                        } else if (walletState.isInitialized) {
                                            "Transactions will appear here after syncing"
                                        } else {
                                            "Add a wallet to get started"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            } else {
                items(visibleTransactions, key = { it.txid }) { tx ->
                    // Look up label: first check transaction label, then address label
                    val label =
                        transactionLabels[tx.txid]
                            ?: tx.address?.let { addressLabels[it] }

                    TransactionItem(
                        transaction = tx,
                        useSats = useSats,
                        label = label,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        onClick = { selectedTransaction = tx },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Progressive "Show More" / "Show All" buttons
                if (hasMore) {
                    item {
                        val remaining = totalCount - visibleCount
                        TextButton(
                            onClick = {
                                displayLimit = if (displayLimit <= 25) {
                                    100
                                } else {
                                    Int.MAX_VALUE
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text =
                                    if (displayLimit <= 25) {
                                        "Show More"
                                    } else {
                                        "Show All ($remaining remaining)"
                                    },
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // QR Scanner Dialog — must be outside LazyColumn
    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showQrScanner = false
                onScanQrResult(code)
            },
            onDismiss = { showQrScanner = false },
        )
    }

    // Quick Receive Dialog — must be outside LazyColumn
    val quickReceiveAddress = walletState.currentAddress
    if (showQuickReceive && quickReceiveAddress != null) {
        var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(quickReceiveAddress) {
            qrBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                generateQrBitmap(quickReceiveAddress)
            }
        }
        val context = LocalContext.current
        var showCopied by remember { mutableStateOf(false) }

        LaunchedEffect(showCopied) {
            if (showCopied) {
                kotlinx.coroutines.delay(3000)
                showCopied = false
            }
        }

        Dialog(
            onDismissRequest = { showQuickReceive = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier =
                    Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // QR Code
                    qrBitmap?.let { bitmap ->
                        Box(
                            modifier =
                                Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Receive address QR",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Address + copy icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "${quickReceiveAddress.take(9)}...${quickReceiveAddress.takeLast(9)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy address",
                            tint = if (showCopied) BitcoinOrange else TextSecondary,
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable {
                                        SecureClipboard.copyAndScheduleClear(
                                            context,
                                            "Address",
                                            quickReceiveAddress,
                                        )
                                        showCopied = true
                                    },
                        )
                    }

                    if (showCopied) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Copied to clipboard!",
                            style = MaterialTheme.typography.bodySmall,
                            color = BitcoinOrange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: TransactionDetails,
    useSats: Boolean = false,
    label: String? = null,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    onClick: () -> Unit = {},
) {
    val isReceived = transaction.amountSats > 0
    val absSats = kotlin.math.abs(transaction.amountSats).toULong()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = DarkCard,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isReceived) {
                                AccentGreen.copy(alpha = 0.1f)
                            } else {
                                AccentRed.copy(alpha = 0.1f)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (isReceived) {
                            Icons.AutoMirrored.Filled.CallReceived
                        } else {
                            Icons.AutoMirrored.Filled.CallMade
                        },
                    contentDescription = if (isReceived) "Received" else "Sent",
                    tint = if (isReceived) AccentGreen else AccentRed,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isReceived) "Received" else "Sent",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = AccentTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Date and time
                Text(
                    text = transaction.timestamp?.let { formatDateTime(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = TextSecondary,
                )
            }

            // Amount and status
            Column(horizontalAlignment = Alignment.End) {
                val amountText =
                    if (privacyMode) {
                        HIDDEN_AMOUNT
                    } else {
                        val sign = if (isReceived) "+" else "-"
                        val amount = formatAmount(absSats, useSats)
                        if (useSats) "$sign$amount sats" else "$sign$amount"
                    }
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isReceived) AccentGreen else AccentRed,
                    textAlign = TextAlign.End,
                )
                // USD value
                if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                    val usdValue = (absSats.toDouble() / 100_000_000.0) * btcPrice
                    Text(
                        text = formatUsd(usdValue),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = TextSecondary,
                        textAlign = TextAlign.End,
                    )
                }
                if (!transaction.isConfirmed) {
                    StatusBadge(
                        label = "Pending",
                        color = BitcoinOrange,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val date = Date(timestamp * 1000) // Convert seconds to milliseconds
    val format = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    return format.format(date)
}

private const val HIDDEN_AMOUNT = "****"

/**
 * Transaction Detail Dialog
 * Shows complete transaction details when user taps on a transaction
 */
@Composable
fun TransactionDetailDialog(
    transaction: TransactionDetails,
    useSats: Boolean = false,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_DISABLED,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    label: String? = null,
    canRbf: Boolean = false,
    canCpfp: Boolean = false,
    availableBalance: ULong = 0UL,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    onFetchVsize: suspend (String) -> Double? = { null },
    onRefreshFees: () -> Unit = {},
    onSpeedUp: ((SpeedUpMethod, Double) -> Unit)? = null,
    onSaveLabel: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isReceived = transaction.amountSats > 0
    val scrollState = rememberScrollState()

    // State for showing copy confirmation
    var showCopiedTxid by remember { mutableStateOf(false) }
    var showCopiedAddress by remember { mutableStateOf(false) }
    var showCopiedChangeAddress by remember { mutableStateOf(false) }

    // Auto-dismiss copy notifications after 3 seconds
    LaunchedEffect(showCopiedTxid) {
        if (showCopiedTxid) {
            kotlinx.coroutines.delay(3000)
            showCopiedTxid = false
        }
    }
    LaunchedEffect(showCopiedAddress) {
        if (showCopiedAddress) {
            kotlinx.coroutines.delay(3000)
            showCopiedAddress = false
        }
    }
    LaunchedEffect(showCopiedChangeAddress) {
        if (showCopiedChangeAddress) {
            kotlinx.coroutines.delay(3000)
            showCopiedChangeAddress = false
        }
    }

    // State for showing Tor Browser error dialog
    var showTorBrowserError by remember { mutableStateOf(false) }

    // State for Speed Up dialog
    var showSpeedUpDialog by remember { mutableStateOf(false) }

    // State for Redirect (cancel) dialog
    var showRedirectDialog by remember { mutableStateOf(false) }
    val canRedirect = canRbf && !isReceived && !transaction.isConfirmed && !transaction.isSelfTransfer

    // State for label editing
    var isEditingLabel by remember { mutableStateOf(false) }
    var labelText by remember { mutableStateOf(label ?: "") }

    // State for fetched vsize from Electrum (fractional: weight/4.0)
    var fetchedVsize by remember { mutableStateOf<Double?>(null) }

    // Fetch actual vsize from Electrum when dialog opens (for sent transactions with fee info)
    LaunchedEffect(transaction.txid) {
        if (!isReceived && transaction.fee != null && transaction.fee > 0UL) {
            fetchedVsize = onFetchVsize(transaction.txid)
        }
    }

    // Check if mempool URL is an onion address
    val isOnionAddress =
        try {
            java.net.URI(mempoolUrl).host?.endsWith(".onion") == true
        } catch (_: Exception) {
            mempoolUrl.endsWith(".onion")
        }

    // Determine if Speed Up is available
    val canSpeedUp = !transaction.isConfirmed && (canRbf || canCpfp)
    val speedUpMethod =
        when {
            canRbf && !isReceived -> SpeedUpMethod.RBF
            canCpfp -> SpeedUpMethod.CPFP
            else -> null
        }

    val amountAbs = kotlin.math.abs(transaction.amountSats).toULong()
    val denominationLabel = if (useSats) "sats" else "BTC"

    // Tor Browser error dialog
    if (showTorBrowserError) {
        TorBrowserErrorDialog(
            onDismiss = { showTorBrowserError = false },
        )
    }

    // Speed Up dialog
    if (showSpeedUpDialog && speedUpMethod != null && onSpeedUp != null) {
        val dialogVsize = fetchedVsize ?: transaction.vsize
        // For CPFP, the spendable output is either the received amount or the change amount
        val isReceived = transaction.amountSats > 0
        val cpfpSpendableOutput =
            if (isReceived) {
                transaction.amountSats.toULong()
            } else {
                transaction.changeAmount ?: 0UL
            }
        SpeedUpDialog(
            method = speedUpMethod,
            currentFee = transaction.fee,
            currentFeeRate = transaction.feeRate,
            vsize = dialogVsize,
            availableBalance = availableBalance,
            cpfpSpendableOutput = cpfpSpendableOutput,
            feeEstimationState = feeEstimationState,
            minFeeRate = minFeeRate,
            useSats = useSats,
            privacyMode = privacyMode,
            onRefreshFees = onRefreshFees,
            onConfirm = { feeRate ->
                onSpeedUp(speedUpMethod, feeRate)
                showSpeedUpDialog = false
                onDismiss()
            },
            onDismiss = { showSpeedUpDialog = false },
        )
    }

    // Redirect (cancel transaction) dialog
    if (showRedirectDialog && canRedirect && onSpeedUp != null) {
        val dialogVsize = fetchedVsize ?: transaction.vsize
        SpeedUpDialog(
            method = SpeedUpMethod.REDIRECT,
            currentFee = transaction.fee,
            currentFeeRate = transaction.feeRate,
            vsize = dialogVsize,
            availableBalance = availableBalance,
            feeEstimationState = feeEstimationState,
            minFeeRate = minFeeRate,
            useSats = useSats,
            privacyMode = privacyMode,
            onRefreshFees = onRefreshFees,
            onConfirm = { feeRate ->
                onSpeedUp(SpeedUpMethod.REDIRECT, feeRate)
                showRedirectDialog = false
                onDismiss()
            },
            onDismiss = { showRedirectDialog = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(scrollState),
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkCard)
                                .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount display (large, centered)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isReceived) AccentGreen.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f),
                            )
                            .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Direction icon
                        Icon(
                            imageVector =
                                when {
                                    transaction.isSelfTransfer -> Icons.AutoMirrored.Filled.CallMade
                                    isReceived -> Icons.AutoMirrored.Filled.CallReceived
                                    else -> Icons.AutoMirrored.Filled.CallMade
                                },
                            contentDescription = null,
                            tint =
                                when {
                                    transaction.isSelfTransfer -> BitcoinOrange
                                    isReceived -> AccentGreen
                                    else -> AccentRed
                                },
                            modifier = Modifier.size(28.dp),
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Direction text
                        Text(
                            text =
                                when {
                                    transaction.isSelfTransfer -> "Self-Transfer"
                                    isReceived -> "Received"
                                    else -> "Sent"
                                },
                            style = MaterialTheme.typography.titleSmall,
                            color =
                                when {
                                    transaction.isSelfTransfer -> BitcoinOrange
                                    isReceived -> AccentGreen
                                    else -> AccentRed
                                },
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Amount (based on denomination setting)
                        Text(
                            text =
                                if (privacyMode) {
                                    HIDDEN_AMOUNT
                                } else {
                                    "${if (isReceived) "+" else "-"}${formatAmount(
                                        amountAbs,
                                        useSats,
                                    )} $denominationLabel"
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isReceived) AccentGreen else AccentRed,
                        )

                        // USD amount (if price is available)
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            val usdValue = (amountAbs.toDouble() / 100_000_000.0) * btcPrice
                            Text(
                                text = formatUsd(usdValue),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Speed Up button (for pending transactions)
                if (canSpeedUp && speedUpMethod != null && onSpeedUp != null) {
                    OutlinedButton(
                        onClick = { showSpeedUpDialog = true },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = BitcoinOrange,
                            ),
                        border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (speedUpMethod) {
                                SpeedUpMethod.RBF -> "Bump Fee (RBF)"
                                SpeedUpMethod.CPFP -> "Bump Fee (CPFP)"
                                SpeedUpMethod.REDIRECT -> "Bump Fee (RBF)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Cancel Transaction button (redirect to self via RBF)
                if (canRedirect && onSpeedUp != null) {
                    OutlinedButton(
                        onClick = { showRedirectDialog = true },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = BitcoinOrange,
                            ),
                        border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cancel Transaction (RBF)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (mempoolServer != SecureStorage.MEMPOOL_DISABLED && mempoolUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val url = "$mempoolUrl/tx/${transaction.txid}"
                            if (isOnionAddress) {
                                // Try to open Tor Browser automatically for onion addresses
                                val torBrowserPackage = "org.torproject.torbrowser"
                                val intent =
                                    Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                        setPackage(torBrowserPackage)
                                    }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // Tor Browser not installed, show error
                                    showTorBrowserError = true
                                }
                            } else {
                                // Open directly for clearnet
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                context.startActivity(intent)
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary,
                            ),
                        border = BorderStroke(1.dp, Color(0xFF9BA3AC)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(getMempoolButtonText(mempoolServer), style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Transaction details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                    ) {
                        // Status section
                        Column {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector =
                                            if (transaction.isConfirmed) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.Schedule
                                            },
                                        contentDescription = null,
                                        tint = if (transaction.isConfirmed) AccentGreen else BitcoinOrange,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (transaction.isConfirmed) "Confirmed" else "Pending",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (transaction.isConfirmed) AccentGreen else BitcoinOrange,
                                    )
                                }
                                if (transaction.timestamp != null) {
                                    Text(
                                        text = formatFullTimestamp(transaction.timestamp * 1000),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        // Transaction ID
                        Column {
                            Text(
                                text = "Transaction ID",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = transaction.txid.take(20) + "..." + transaction.txid.takeLast(8),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                            .clickable {
                                                SecureClipboard.copyAndScheduleClear(
                                                    context,
                                                    "Transaction ID",
                                                    transaction.txid,
                                                )
                                                showCopiedTxid = true
                                            },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = if (showCopiedTxid) BitcoinOrange else TextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            // Show "Copied!" text
                            if (showCopiedTxid) {
                                Text(
                                    text = "Copied to clipboard!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        // Address (recipient for sent, receiving address for received, or destination for self-transfer)
                        if (transaction.address != null) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isReceived && !transaction.isSelfTransfer) "Received at" else "Recipient",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                    if (transaction.isSelfTransfer) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(Self-transfer)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = BorderColor,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = transaction.address.take(14) + "..." + transaction.address.takeLast(8),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        transaction.addressAmount?.let { amount ->
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text =
                                                    if (privacyMode) {
                                                        HIDDEN_AMOUNT
                                                    } else {
                                                        "${if (isReceived || transaction.isSelfTransfer) "+" else "-"}${formatAmount(
                                                            amount,
                                                            useSats,
                                                        )} $denominationLabel"
                                                    },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isReceived || transaction.isSelfTransfer) AccentGreen else AccentRed,
                                            )
                                        }
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier =
                                            Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DarkSurfaceVariant)
                                                .clickable {
                                                    SecureClipboard.copyAndScheduleClear(
                                                        context,
                                                        "Address",
                                                        transaction.address,
                                                    )
                                                    showCopiedAddress = true
                                                },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Address",
                                            tint = if (showCopiedAddress) BitcoinOrange else TextSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                // Show "Copied!" text
                                if (showCopiedAddress) {
                                    Text(
                                        text = "Copied to clipboard!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BitcoinOrange,
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )

                            // Change Address (for sent transactions and self-transfers)
                            if ((!isReceived || transaction.isSelfTransfer) && transaction.changeAddress != null && transaction.changeAddress != transaction.address) {
                                Column {
                                    Text(
                                        text = "Change returned to",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = transaction.changeAddress.take(14) + "..." + transaction.changeAddress.takeLast(8),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                            transaction.changeAmount?.let { amount ->
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text =
                                                        if (privacyMode) {
                                                            HIDDEN_AMOUNT
                                                        } else {
                                                            "+${formatAmount(
                                                                amount,
                                                                useSats,
                                                            )} $denominationLabel"
                                                        },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = AccentGreen,
                                                )
                                            }
                                        }
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier =
                                                Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(DarkSurfaceVariant)
                                                    .clickable {
                                                        SecureClipboard.copyAndScheduleClear(
                                                            context,
                                                            "Change Address",
                                                            transaction.changeAddress,
                                                        )
                                                        showCopiedChangeAddress = true
                                                    },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Change Address",
                                                tint = if (showCopiedChangeAddress) BitcoinOrange else TextSecondary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    // Show "Copied!" text
                                    if (showCopiedChangeAddress) {
                                        Text(
                                            text = "Copied to clipboard!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange,
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                            }
                        }

                        // Fee (if available and it's a sent transaction)
                        if (!isReceived && transaction.fee != null && transaction.fee > 0UL) {
                            Column {
                                Text(
                                    text = "Network Fee",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                // Use fetched vsize from Electrum if available, otherwise fall back to BDK's weight/4.0
                                val displayVsize: Double? = fetchedVsize ?: transaction.vsize
                                // Recalculate fee rate using the fractional vsize
                                val displayFeeRate =
                                    if (displayVsize != null && displayVsize > 0.0) {
                                        transaction.fee.toDouble() / displayVsize
                                    } else {
                                        transaction.feeRate
                                    }
                                if (displayFeeRate != null && displayVsize != null) {
                                    Text(
                                        text = "${formatFeeRate(
                                            displayFeeRate,
                                        )} sat/vB • ${formatVBytes(displayVsize)} vB",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                }
                                Text(
                                    text =
                                        if (privacyMode) {
                                            HIDDEN_AMOUNT
                                        } else {
                                            "-${formatAmount(
                                                transaction.fee,
                                                useSats,
                                            )} $denominationLabel"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BitcoinOrange,
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                        }

                        // Label section
                        Column {
                            Text(
                                text = "Label",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isEditingLabel) {
                                OutlinedTextField(
                                    value = labelText,
                                    onValueChange = { labelText = it },
                                    placeholder = { Text("Enter label", color = TextSecondary.copy(alpha = 0.5f)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BitcoinOrange,
                                            unfocusedBorderColor = BorderColor,
                                            cursorColor = BitcoinOrange,
                                        ),
                                    trailingIcon = {
                                        TextButton(
                                            onClick = {
                                                onSaveLabel(labelText)
                                                isEditingLabel = false
                                            },
                                        ) {
                                            Text("Save", color = BitcoinOrange)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else if (labelText.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AccentTeal,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Card(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { isEditingLabel = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                        border = BorderStroke(1.dp, BorderColor),
                                    ) {
                                        Text(
                                            text = "Edit",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            } else {
                                Card(
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { isEditingLabel = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    border = BorderStroke(1.dp, BorderColor),
                                ) {
                                    Text(
                                        text = "Add",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Close button
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Close", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Tor Browser Error Dialog
 * Shows when Tor Browser is not installed but needed for onion addresses
 */
@Composable
private fun TorBrowserErrorDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = AccentRed,
                    modifier = Modifier.size(48.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header
                Text(
                    text = "Tor Browser Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Tor Browser is required to view onion links. Please install it from the Play Store or F-Droid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Close button
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Close", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Speed Up Transaction Dialog
 * Allows user to set a new fee rate for RBF or CPFP
 */
@Composable
private fun SpeedUpDialog(
    method: SpeedUpMethod,
    currentFee: ULong?,
    currentFeeRate: Double?,
    vsize: Double?,
    availableBalance: ULong = 0UL,
    cpfpSpendableOutput: ULong = 0UL,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    useSats: Boolean = true,
    privacyMode: Boolean = false,
    onRefreshFees: () -> Unit = {},
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogTitle =
        when (method) {
            SpeedUpMethod.RBF -> "Bump Fee (RBF)"
            SpeedUpMethod.CPFP -> "Bump Fee (CPFP)"
            SpeedUpMethod.REDIRECT -> "Cancel Transaction"
        }
    val dialogDescription =
        when (method) {
            SpeedUpMethod.RBF -> "Replace this transaction with a higher fee (RBF)"
            SpeedUpMethod.CPFP -> "Create a child transaction with high fee to speed up confirmation (CPFP)"
            SpeedUpMethod.REDIRECT -> "Redirect all funds back to your wallet. Requires a higher fee than the original."
        }
    var appliedFeeRate by remember { mutableDoubleStateOf(0.0) }
    var rawCustomFeeRate by remember { mutableStateOf<Double?>(null) }
    val enteredFeeRate = rawCustomFeeRate ?: appliedFeeRate
    val isBelowMinFeeRate = enteredFeeRate > 0.0 && enteredFeeRate < minFeeRate
    val isBelowCurrentFeeRate =
        currentFeeRate != null &&
            enteredFeeRate > 0.0 &&
            enteredFeeRate <= currentFeeRate
    val isValidFeeRate =
        appliedFeeRate > 0.0 &&
            enteredFeeRate <= MAX_FEE_RATE_SAT_VB &&
            !isBelowMinFeeRate &&
            !isBelowCurrentFeeRate

    // Estimated child tx vsize for CPFP (1 input + 1 output, conservative estimate)
    // P2WPKH: ~110 vB, P2TR: ~111 vB, adding buffer for safety
    val estimatedChildVsize = 150L

    // Dust limit - use 546 sats (Bitcoin Core default, covers all output types)
    // P2TR actual dust is ~387, P2WPKH is ~294
    val dustLimit = 546L

    // Calculate costs differently for RBF vs CPFP vs REDIRECT
    val additionalCost: Long? =
        when (method) {
            SpeedUpMethod.RBF, SpeedUpMethod.REDIRECT -> {
                if (vsize != null && appliedFeeRate > 0.0 && currentFee != null) {
                    val newTotalFee = (appliedFeeRate * vsize).toLong()
                    (newTotalFee - currentFee.toLong()).coerceAtLeast(0)
                } else {
                    null
                }
            }
            SpeedUpMethod.CPFP -> {
                if (appliedFeeRate > 0.0) {
                    val effectiveFeeRate = kotlin.math.ceil(appliedFeeRate).toLong()
                    (effectiveFeeRate * estimatedChildVsize)
                } else {
                    null
                }
            }
        }

    // For CPFP, we can use the parent output PLUS confirmed wallet balance
    // BDK will add more UTXOs if needed to cover the fee
    // For RBF, we need confirmed balance to cover the fee bump
    val fundsForFee =
        when (method) {
            SpeedUpMethod.RBF -> availableBalance
            SpeedUpMethod.REDIRECT -> availableBalance
            SpeedUpMethod.CPFP -> cpfpSpendableOutput + availableBalance
        }

    // Check affordability - need funds >= fee + dust (for change output)
    val canAfford =
        when (method) {
            SpeedUpMethod.RBF -> additionalCost == null || additionalCost <= 0 || additionalCost.toULong() <= fundsForFee
            SpeedUpMethod.REDIRECT -> true // Fee comes from the original inputs, not wallet balance
            SpeedUpMethod.CPFP -> {
                if (additionalCost == null || additionalCost <= 0) {
                    true
                } else {
                    // Need: totalFunds >= fee + dustLimit (to have valid change output)
                    fundsForFee.toLong() >= additionalCost + dustLimit
                }
            }
        }

    // Check if parent output alone can cover fee + dust
    val parentCanCoverAlone =
        method == SpeedUpMethod.CPFP &&
            additionalCost != null &&
            cpfpSpendableOutput.toLong() > additionalCost + dustLimit

    // If parent can't cover alone, wallet UTXOs will be consolidated
    val willConsolidateWallet =
        method == SpeedUpMethod.CPFP &&
            additionalCost != null &&
            !parentCanCoverAlone &&
            canAfford

    // Fetch fee estimates when dialog opens
    LaunchedEffect(Unit) {
        onRefreshFees()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
            ) {
                // Header
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Method description
                Text(
                    text = dialogDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Current fee info
                if (currentFeeRate != null || (currentFee != null && currentFee > 0UL)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                        ) {
                            Text(
                                text = "Current Transaction:",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (currentFeeRate != null) {
                                Text(
                                    text = "Fee rate: ${formatFeeRate(currentFeeRate)} sat/vB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            if (currentFee != null && currentFee > 0UL) {
                                Text(
                                    text =
                                        if (privacyMode) {
                                            "Fee: $HIDDEN_AMOUNT"
                                        } else {
                                            "Fee: ${formatAmount(
                                                currentFee,
                                                useSats,
                                            )} ${if (useSats) "sats" else "BTC"}"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                FeeRateSection(
                    feeEstimationState = feeEstimationState,
                    currentFeeRate = appliedFeeRate,
                    minFeeRate = minFeeRate,
                    onFeeRateChange = { appliedFeeRate = it },
                    onRawCustomFeeRateChange = { rawCustomFeeRate = it },
                    onRefreshFees = onRefreshFees,
                    enabled = true,
                )

                if (isBelowMinFeeRate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fee rate is below the minimum (${formatFeeRate(minFeeRate)} sat/vB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                currentFeeRate?.takeIf { isBelowCurrentFeeRate }?.let { currentRate ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fee rate must be higher than current (${formatFeeRate(currentRate)} sat/vB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // New Transaction card (shows estimated fee info)
                if (appliedFeeRate > 0.0 && isValidFeeRate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                        ) {
                            Text(
                                text = when (method) {
                                    SpeedUpMethod.RBF -> "Replacement Transaction:"
                                    SpeedUpMethod.REDIRECT -> "Redirect Transaction:"
                                    SpeedUpMethod.CPFP -> "Child Transaction (CPFP):"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Target fee rate: ${formatFeeRate(appliedFeeRate)} sat/vB",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                            )
                            if (additionalCost != null && additionalCost > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val costLabel = if (method == SpeedUpMethod.CPFP) "Child tx fee" else "Additional cost"
                                Text(
                                    text =
                                        if (privacyMode) {
                                            "$costLabel: $HIDDEN_AMOUNT"
                                        } else {
                                            "$costLabel: ${formatAmount(
                                                additionalCost.toULong(),
                                                useSats,
                                            )} ${if (useSats) "sats" else "BTC"}"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canAfford) BitcoinOrange else ErrorRed,
                                )
                                if (method == SpeedUpMethod.CPFP) {
                                    Text(
                                        text = "($estimatedChildVsize vB × ${kotlin.math.ceil(
                                            appliedFeeRate,
                                        ).toLong()} sat/vB)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                    if (willConsolidateWallet) {
                                        Text(
                                            text = "Will consolidate all wallet UTXOs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BitcoinOrange,
                                        )
                                    }
                                }
                                if (!canAfford) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (method == SpeedUpMethod.CPFP) {
                                        val needed = additionalCost + dustLimit
                                        Text(
                                            text = "Insufficient funds",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed,
                                        )
                                        Text(
                                            text =
                                                if (privacyMode) {
                                                    "Need: $HIDDEN_AMOUNT (fee + dust)"
                                                } else {
                                                    "Need: ${formatAmount(
                                                        needed.toULong(),
                                                        useSats,
                                                    )} ${if (useSats) "sats" else "BTC"} (fee + dust)"
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ErrorRed,
                                        )
                                        Text(
                                            text =
                                                if (privacyMode) {
                                                    "Have: $HIDDEN_AMOUNT (output + wallet)"
                                                } else {
                                                    "Have: ${formatAmount(
                                                        fundsForFee,
                                                        useSats,
                                                    )} ${if (useSats) "sats" else "BTC"} (output + wallet)"
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                        )
                                    } else {
                                        Text(
                                            text =
                                                if (privacyMode) {
                                                    "Insufficient funds"
                                                } else {
                                                    "Insufficient funds (Available: ${formatAmount(
                                                        fundsForFee,
                                                        useSats,
                                                    )} ${if (useSats) "sats" else "BTC"})"
                                                },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm button
                val canConfirm = isValidFeeRate && canAfford
                IbisButton(
                    onClick = { onConfirm(appliedFeeRate) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    enabled = canConfirm,
                ) {
                    Text(
                        text =
                            when (method) {
                                SpeedUpMethod.RBF -> "Bump Fee"
                                SpeedUpMethod.CPFP -> "Bump Fee"
                                SpeedUpMethod.REDIRECT -> "Cancel Transaction"
                            },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cancel button
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Get the appropriate button text for the block explorer button
 * based on the selected mempool server
 */

private fun getMempoolButtonText(mempoolServer: String): String {
    return when (mempoolServer) {
        SecureStorage.MEMPOOL_SPACE -> "View on mempool.space"
        SecureStorage.MEMPOOL_ONION -> "View on mempool.space (onion)"
        SecureStorage.MEMPOOL_CUSTOM -> "View on custom block explorer"
        else -> "View in block explorer"
    }
}
