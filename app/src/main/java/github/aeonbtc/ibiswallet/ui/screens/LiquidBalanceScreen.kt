@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTransaction
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.LiquidWalletState
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentSession
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.LiquidTransactionItem
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.theme.AccentBlue
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.matchesTimestampSearch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidBalanceScreen(
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    liquidExplorer: String = SecureStorage.LIQUID_EXPLORER_DISABLED,
    liquidExplorerUrl: String = "",
    onTogglePrivacy: () -> Unit = {},
    liquidTransactionLabels: Map<String, String> = emptyMap(),
    lookupPendingLightningPayment: (String) -> PendingLightningPaymentSession? = { null },
    onSaveLiquidTransactionLabel: (String, String) -> Unit = { _, _ -> },
    onToggleDenomination: () -> Unit = {},
    onScanQrResult: (String) -> Unit = {},
    liquidState: LiquidWalletState = LiquidWalletState(),
    onSyncLiquid: () -> Unit = {},
) {
    var selectedLiquidTransaction by remember { mutableStateOf<LiquidTransaction?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showQuickReceive by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSwapTransactions by remember { mutableStateOf(false) }
    var showLightningTransactions by remember { mutableStateOf(false) }
    var showLiquidTransactions by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var displayLimit by remember { mutableIntStateOf(25) }

    val useSats = denomination == SecureStorage.DENOMINATION_SATS
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

    selectedLiquidTransaction?.let { tx ->
        LiquidTransactionDetailDialog(
            transaction = tx,
            useSats = useSats,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            liquidExplorer = liquidExplorer,
            liquidExplorerUrl = liquidExplorerUrl,
            label = liquidTransactionLabels[tx.txid],
            pendingLightningPayment = lookupPendingLightningPayment(tx.txid),
            onSaveLabel = { label -> onSaveLiquidTransactionLabel(tx.txid, label) },
            onDismiss = { selectedLiquidTransaction = null },
        )
    }

    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(liquidState.isSyncing) {
        if (!liquidState.isSyncing) {
            isPullRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = {
            if (liquidState.isInitialized) {
                isPullRefreshing = true
                onSyncLiquid()
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
                        val progress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                        translationY = progress * 40f
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .padding(bottom = 4.dp)
                                    .align(Alignment.TopCenter),
                        ) {
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

                            val syncEnabled = liquidState.isInitialized && !liquidState.isSyncing
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .align(Alignment.CenterEnd)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .clickable(enabled = syncEnabled) { onSyncLiquid() },
                            ) {
                                if (liquidState.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = LiquidTeal,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val lbtcSats = liquidState.balanceSats
                            Text(
                                text =
                                    if (privacyMode) {
                                        HIDDEN_AMOUNT
                                    } else if (useSats) {
                                        "${formatAmount(lbtcSats.toULong(), true)} sats"
                                    } else {
                                        "\u20BF ${formatAmount(lbtcSats.toULong(), false)}"
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
                            if (btcPrice != null && btcPrice > 0) {
                                val usdValue = (lbtcSats.toDouble() / 100_000_000.0) * btcPrice
                                Text(
                                    text = if (privacyMode) HIDDEN_AMOUNT else formatUsd(usdValue),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary,
                                )
                            }
                        }

                        val quickReceiveEnabled = liquidState.currentAddress != null
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
                                tint = LiquidTeal,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable(enabled = liquidState.isInitialized) {
                                        showQrScanner = true
                                    },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = LiquidTeal,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LiquidTransactionFilterButton(
                            icon = Icons.Default.SwapHoriz,
                            contentDescription = "Filter swaps",
                            tint = BitcoinOrange,
                            isSelected = showSwapTransactions,
                            onClick = { showSwapTransactions = !showSwapTransactions },
                        )
                        LiquidTransactionFilterButton(
                            icon = Icons.Default.Bolt,
                            contentDescription = "Filter Lightning transactions",
                            tint = LightningYellow,
                            isSelected = showLightningTransactions,
                            onClick = { showLightningTransactions = !showLightningTransactions },
                        )
                        LiquidTransactionFilterButton(
                            icon = Icons.Default.WaterDrop,
                            contentDescription = "Filter Liquid transactions",
                            tint = LiquidTeal,
                            isSelected = showLiquidTransactions,
                            onClick = { showLiquidTransactions = !showLiquidTransactions },
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
                                tint = if (isSearchActive) LiquidTeal else TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

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
                                focusedBorderColor = LiquidTeal,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = LiquidTeal,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            val hasSourceFilters = showSwapTransactions || showLightningTransactions || showLiquidTransactions
            val sourceFilteredTransactions =
                if (!hasSourceFilters) {
                    liquidState.transactions
                } else {
                    liquidState.transactions.filter { tx ->
                        when (tx.source) {
                            LiquidTxSource.CHAIN_SWAP -> showSwapTransactions
                            LiquidTxSource.LIGHTNING_RECEIVE_SWAP,
                            LiquidTxSource.LIGHTNING_SEND_SWAP,
                            -> showLightningTransactions
                            LiquidTxSource.NATIVE -> showLiquidTransactions
                        }
                    }
                }
            val filteredTransactions = if (searchQuery.isBlank()) {
                sourceFilteredTransactions
            } else {
                val query = searchQuery.lowercase()
                sourceFilteredTransactions.filter { tx ->
                    val label = liquidTransactionLabels[tx.txid].orEmpty()
                    val memo = tx.memo
                    val walletAddress = tx.walletAddress.orEmpty()
                    val changeAddress = tx.changeAddress.orEmpty()
                    tx.txid.lowercase().contains(query) ||
                        label.lowercase().contains(query) ||
                        memo.lowercase().contains(query) ||
                        walletAddress.lowercase().contains(query) ||
                        changeAddress.lowercase().contains(query) ||
                        matchesTimestampSearch(tx.timestamp, searchQuery)
                }
            }
            val isSearching = searchQuery.isNotBlank()
            val visibleTransactions = if (isSearching) {
                filteredTransactions
            } else {
                filteredTransactions.take(displayLimit)
            }
            val totalCount = filteredTransactions.size
            val visibleCount = visibleTransactions.size
            val hasMore = !isSearching && visibleCount < totalCount

            if (filteredTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching Liquid transactions" else "No Liquid transactions yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        "Try a different search term"
                                    } else if (liquidState.isInitialized) {
                                        "Transactions will appear here after syncing"
                                    } else {
                                        "Liquid is loading for this wallet"
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
                    LiquidTransactionItem(
                        tx = tx,
                        denomination = denomination,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        label = liquidTransactionLabels[tx.txid],
                        onClick = { selectedLiquidTransaction = tx },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

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

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showQrScanner = false
                onScanQrResult(code)
            },
            onDismiss = { showQrScanner = false },
        )
    }

    val quickReceiveAddress = liquidState.currentAddress
    if (showQuickReceive && quickReceiveAddress != null) {
        var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(quickReceiveAddress) {
            qrBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                generateQrBitmap(quickReceiveAddress)
            }
        }
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
                            tint = if (showCopied) LiquidTeal else TextSecondary,
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable {
                                        SecureClipboard.copyAndScheduleClear(
                                            context,
                                            "Liquid Address",
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
                            color = LiquidTeal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidTransactionDetailDialog(
    transaction: LiquidTransaction,
    useSats: Boolean,
    btcPrice: Double?,
    privacyMode: Boolean,
    liquidExplorer: String = SecureStorage.LIQUID_EXPLORER_DISABLED,
    liquidExplorerUrl: String = "",
    label: String? = null,
    pendingLightningPayment: PendingLightningPaymentSession? = null,
    onSaveLabel: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val effectiveLabel = label?.takeIf { it.isNotBlank() } ?: transaction.memo.takeIf { it.isNotBlank() }
    val swapDetails = transaction.swapDetails?.takeIf { transaction.source == LiquidTxSource.CHAIN_SWAP }
    val swapRole = swapDetails?.role
    val isReceive =
        when (swapRole) {
            LiquidSwapTxRole.FUNDING -> false
            LiquidSwapTxRole.SETTLEMENT -> true
            null -> transaction.balanceSatoshi >= 0
        }
    val amountAbs =
        when (swapRole) {
            LiquidSwapTxRole.FUNDING ->
                (swapDetails.sendAmountSats.takeIf { it > 0L } ?: kotlin.math.abs(transaction.balanceSatoshi)).toULong()
            LiquidSwapTxRole.SETTLEMENT ->
                (swapDetails.expectedReceiveAmountSats.takeIf { it > 0L } ?: kotlin.math.abs(transaction.balanceSatoshi))
                    .toULong()
            null -> kotlin.math.abs(transaction.balanceSatoshi).toULong()
        }
    val accentColor = if (isReceive) AccentGreen else AccentRed
    val showSourceSection =
        transaction.source != LiquidTxSource.NATIVE &&
            transaction.swapDetails == null &&
            (transaction.walletAddress == null || !isReceive) &&
            transaction.changeAddress == null
    val lightningPaymentDetails =
        pendingLightningPayment?.takeIf {
            transaction.source == LiquidTxSource.LIGHTNING_SEND_SWAP
        }
    val scrollState = rememberScrollState()
    val explorerUrl =
        liquidExplorerUrl.takeIf { it.isNotBlank() }?.let { "$it/tx/${transaction.txid}" }.orEmpty()
    val isOnionExplorer =
        try {
            java.net.URI(liquidExplorerUrl).host?.endsWith(".onion") == true
        } catch (_: Exception) {
            liquidExplorerUrl.endsWith(".onion")
        }

    var showCopiedTxid by remember { mutableStateOf(false) }
    var showCopiedAddress by remember { mutableStateOf(false) }
    var showCopiedChangeAddress by remember { mutableStateOf(false) }
    var copiedSwapField by remember { mutableStateOf<String?>(null) }
    var swapDetailsExpanded by remember { mutableStateOf(false) }
    var isEditingLabel by remember { mutableStateOf(false) }
    var labelText by remember(effectiveLabel) { mutableStateOf(effectiveLabel.orEmpty()) }
    var showTorBrowserError by remember { mutableStateOf(false) }

    if (showTorBrowserError) {
        LiquidTorBrowserErrorDialog(
            onDismiss = { showTorBrowserError = false },
        )
    }

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
    LaunchedEffect(copiedSwapField) {
        if (copiedSwapField != null) {
            kotlinx.coroutines.delay(3000)
            copiedSwapField = null
        }
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

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.1f))
                            .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector =
                                when (transaction.type) {
                                    github.aeonbtc.ibiswallet.data.model.LiquidTxType.SWAP -> Icons.Default.SwapHoriz
                                    github.aeonbtc.ibiswallet.data.model.LiquidTxType.RECEIVE -> Icons.AutoMirrored.Filled.CallReceived
                                    github.aeonbtc.ibiswallet.data.model.LiquidTxType.SEND -> Icons.AutoMirrored.Filled.CallMade
                                },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = liquidTransactionKindLabel(transaction),
                            style = MaterialTheme.typography.titleSmall,
                            color = accentColor,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (privacyMode) {
                                HIDDEN_AMOUNT
                            } else {
                                "${if (isReceive) "+" else "-"}${formatAmount(amountAbs, useSats)} ${liquidBalanceUnitLabel(useSats)}"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatUsd(amountAbs.toDouble() / 100_000_000.0 * btcPrice),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (liquidExplorer != SecureStorage.LIQUID_EXPLORER_DISABLED && explorerUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            if (isOnionExplorer) {
                                val torBrowserPackage = "org.torproject.torbrowser"
                                val intent =
                                    Intent(Intent.ACTION_VIEW, explorerUrl.toUri()).apply {
                                        setPackage(torBrowserPackage)
                                    }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    showTorBrowserError = true
                                }
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW, explorerUrl.toUri())
                                context.startActivity(intent)
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = BorderStroke(1.dp, Color(0xFF9BA3AC)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(liquidExplorerButtonText(liquidExplorer), style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

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
                        Column {
                            Text("Status", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector =
                                            if (transaction.height != null) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.Schedule
                                            },
                                        contentDescription = null,
                                        tint = if (transaction.height != null) AccentGreen else LiquidTeal,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (transaction.height != null) "Confirmed" else "Pending",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (transaction.height != null) AccentGreen else LiquidTeal,
                                    )
                                }
                                transaction.timestamp?.let {
                                    Text(
                                        text = formatFullTimestamp(it * 1000),
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

                        Text("Transaction ID", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                                "Liquid Transaction ID",
                                                transaction.txid,
                                            )
                                            showCopiedTxid = true
                                        },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Transaction ID",
                                    tint = if (showCopiedTxid) LiquidTeal else TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (showCopiedTxid) {
                            Text(
                                text = "Copied to clipboard!",
                                style = MaterialTheme.typography.bodySmall,
                                color = LiquidTeal,
                            )
                        }

                        if (isReceive && transaction.walletAddress != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Column {
                                Text("Received at", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = transaction.walletAddress.take(14) + "..." + transaction.walletAddress.takeLast(8),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        transaction.walletAddressAmountSats?.let { amount ->
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = if (privacyMode) {
                                                    HIDDEN_AMOUNT
                                                } else {
                                                    "+${formatAmount(amount.toULong(), useSats)} ${liquidBalanceUnitLabel(useSats)}"
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
                                                        "Liquid Address",
                                                        transaction.walletAddress,
                                                    )
                                                    showCopiedAddress = true
                                                },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Address",
                                            tint = if (showCopiedAddress) LiquidTeal else TextSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                if (showCopiedAddress) {
                                    Text(
                                        text = "Copied to clipboard!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LiquidTeal,
                                    )
                                }
                            }
                        }

                        if (!isReceive && transaction.changeAddress != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Column {
                                Text("Change returned to", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
                                        transaction.changeAmountSats?.let { amount ->
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = if (privacyMode) {
                                                    HIDDEN_AMOUNT
                                                } else {
                                                    "+${formatAmount(amount.toULong(), useSats)} ${liquidBalanceUnitLabel(useSats)}"
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
                                            tint = if (showCopiedChangeAddress) LiquidTeal else TextSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                if (showCopiedChangeAddress) {
                                    Text(
                                        text = "Copied to clipboard!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LiquidTeal,
                                    )
                                }
                            }
                        }

                        if (!isReceive && transaction.fee > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Text("Network Fee", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (privacyMode) {
                                    HIDDEN_AMOUNT
                                } else {
                                    "-${formatAmount(transaction.fee.toULong(), useSats)} ${liquidBalanceUnitLabel(useSats)}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = LiquidTeal,
                            )
                        }

                        if (showSourceSection) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )

                            Text("Source", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = transactionSourceDetail(transaction),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )

                            lightningPaymentDetails?.let { session ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Swap ID", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = truncateMiddle(session.swapId, 28),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Original payment input", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = truncateMiddle(session.paymentInput, 40),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        Text("Label", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
                                        focusedBorderColor = LiquidTeal,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = LiquidTeal,
                                    ),
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            onSaveLabel(labelText)
                                            isEditingLabel = false
                                        },
                                    ) {
                                        Text("Save", color = LiquidTeal)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (labelText.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                        transaction.swapDetails?.let { swapDetails ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { swapDetailsExpanded = !swapDetailsExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Swap Details",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = if (swapDetailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (swapDetailsExpanded) "Collapse swap details" else "Expand swap details",
                                    tint = LiquidTeal,
                                )
                            }

                            if (swapDetailsExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SwapDetailBadge(
                                    text = swapProviderLabel(swapDetails.service),
                                    accentColor = swapProviderColor(swapDetails.service),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SwapDetailDirectionBadge(
                                    direction = swapDetails.direction,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SwapDetailCopyRow(
                                    label = if (swapDetails.service == SwapService.BOLTZ) "Boltz Swap ID" else "SideSwap Order ID",
                                    value = swapDetails.swapId,
                                    copyLabel = "Swap ID",
                                    copied = copiedSwapField == "swap_id",
                                    onCopy = {
                                        SecureClipboard.copyAndScheduleClear(context, "Swap ID", swapDetails.swapId)
                                        copiedSwapField = "swap_id"
                                    },
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SwapDetailCopyRow(
                                    label = swapDepositLabel(swapDetails),
                                    value = swapDetails.depositAddress,
                                    copyLabel = "Swap Deposit Address",
                                    amountText = swapPrimaryAmountText(swapDetails, useSats, privacyMode),
                                    amountColor = swapPrimaryAmountColor(swapDetails.direction),
                                    copied = copiedSwapField == "deposit_address",
                                    onCopy = {
                                        SecureClipboard.copyAndScheduleClear(
                                            context,
                                            "Swap Deposit Address",
                                            swapDetails.depositAddress,
                                        )
                                        copiedSwapField = "deposit_address"
                                    },
                                )
                                swapDetails.receiveAddress?.let { receiveAddress ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    SwapDetailCopyRow(
                                        label = swapReceiveLabel(swapDetails),
                                        value = receiveAddress,
                                        copyLabel = "Swap Destination Address",
                                        amountText = swapExpectedReceiveText(swapDetails, useSats, privacyMode),
                                        amountColor = swapExpectedReceiveColor(swapDetails.direction),
                                        copied = copiedSwapField == "receive_address",
                                        onCopy = {
                                            SecureClipboard.copyAndScheduleClear(
                                                context,
                                                "Swap Destination Address",
                                                receiveAddress,
                                            )
                                            copiedSwapField = "receive_address"
                                        },
                                    )
                                }
                                swapDetails.refundAddress?.let { refundAddress ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    SwapDetailCopyRow(
                                        label = "Refund Address",
                                        value = refundAddress,
                                        copyLabel = "Refund Address",
                                        copied = copiedSwapField == "refund_address",
                                        onCopy = {
                                            SecureClipboard.copyAndScheduleClear(
                                                context,
                                                "Refund Address",
                                                refundAddress,
                                            )
                                            copiedSwapField = "refund_address"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
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

private fun liquidExplorerButtonText(explorer: String): String {
    return when (explorer) {
        SecureStorage.LIQUID_EXPLORER_LIQUID_NETWORK -> "View on liquid.network"
        SecureStorage.LIQUID_EXPLORER_LIQUID_NETWORK_ONION -> "View on liquid.network (onion)"
        SecureStorage.LIQUID_EXPLORER_BLOCKSTREAM -> "View on Blockstream"
        SecureStorage.LIQUID_EXPLORER_CUSTOM -> "View on custom Liquid explorer"
        else -> "View on Liquid Explorer"
    }
}

@Composable
private fun LiquidTorBrowserErrorDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
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
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = AccentRed,
                    modifier = Modifier.size(48.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                )

                Spacer(modifier = Modifier.height(24.dp))

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

@Composable
private fun SwapDetailCopyRow(
    label: String,
    value: String,
    copyLabel: String,
    amountText: String? = null,
    amountColor: Color = MaterialTheme.colorScheme.onBackground,
    copied: Boolean,
    onCopy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = truncateSwapDetailValue(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                amountText?.let {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = amountColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        .clickable(onClick = onCopy),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $copyLabel",
                    tint = if (copied) LiquidTeal else TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (copied) {
            Text(
                text = "Copied to clipboard!",
                style = MaterialTheme.typography.bodySmall,
                color = LiquidTeal,
            )
        }
    }
}

@Composable
private fun SwapDetailBadge(
    text: String,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SwapDetailDirectionBadge(
    direction: SwapDirection,
) {
    val fromText = if (direction == SwapDirection.BTC_TO_LBTC) "BTC" else "L-BTC"
    val toText = if (direction == SwapDirection.BTC_TO_LBTC) "L-BTC" else "BTC"
    val fromColor = if (direction == SwapDirection.BTC_TO_LBTC) BitcoinOrange else LiquidTeal
    val toColor = if (direction == SwapDirection.BTC_TO_LBTC) LiquidTeal else BitcoinOrange

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(DarkSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = fromText,
            style = MaterialTheme.typography.labelMedium,
            color = fromColor,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "->",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = toText,
            style = MaterialTheme.typography.labelMedium,
            color = toColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun swapProviderLabel(service: SwapService): String =
    when (service) {
        SwapService.BOLTZ -> "Boltz"
        SwapService.SIDESWAP -> "SideSwap"
    }

private fun swapProviderColor(service: SwapService): Color =
    when (service) {
        SwapService.BOLTZ -> AccentBlue
        SwapService.SIDESWAP -> LiquidTeal
    }

private fun swapDepositLabel(details: LiquidSwapDetails): String =
    when (details.direction) {
        SwapDirection.BTC_TO_LBTC -> "Bitcoin Funding Address"
        SwapDirection.LBTC_TO_BTC -> "Liquid Swap Deposit Address"
    }

private fun swapReceiveLabel(details: LiquidSwapDetails): String =
    when (details.direction) {
        SwapDirection.BTC_TO_LBTC -> "Liquid Payout Address"
        SwapDirection.LBTC_TO_BTC -> "Bitcoin Destination Address"
    }

private fun swapPrimaryAmountColor(direction: SwapDirection): Color =
    when (direction) {
        SwapDirection.BTC_TO_LBTC -> BitcoinOrange
        SwapDirection.LBTC_TO_BTC -> LiquidTeal
    }

private fun swapExpectedReceiveColor(direction: SwapDirection): Color =
    when (direction) {
        SwapDirection.BTC_TO_LBTC -> LiquidTeal
        SwapDirection.LBTC_TO_BTC -> BitcoinOrange
    }

private fun swapPrimaryAmountText(
    details: LiquidSwapDetails,
    useSats: Boolean,
    privacyMode: Boolean,
): String {
    val asset = when (details.direction) {
        SwapDirection.BTC_TO_LBTC -> "BTC"
        SwapDirection.LBTC_TO_BTC -> "L-BTC"
    }
    return formatSwapAssetAmount(details.sendAmountSats, asset, useSats, privacyMode)
}

private fun swapExpectedReceiveText(
    details: LiquidSwapDetails,
    useSats: Boolean,
    privacyMode: Boolean,
): String {
    val asset = when (details.direction) {
        SwapDirection.BTC_TO_LBTC -> "L-BTC"
        SwapDirection.LBTC_TO_BTC -> "BTC"
    }
    return formatSwapAssetAmount(details.expectedReceiveAmountSats, asset, useSats, privacyMode)
}

private fun formatSwapAssetAmount(
    amountSats: Long,
    asset: String,
    useSats: Boolean,
    privacyMode: Boolean,
): String {
    if (privacyMode) return HIDDEN_AMOUNT
    return "${formatAmount(amountSats.toULong(), useSats)} ${liquidBalanceUnitLabel(useSats)} $asset"
}

private fun truncateSwapDetailValue(
    value: String,
    leadingChars: Int = 16,
    trailingChars: Int = 8,
): String {
    if (value.length <= leadingChars + trailingChars + 3) return value
    return "${value.take(leadingChars)}...${value.takeLast(trailingChars)}"
}

private const val HIDDEN_AMOUNT = "****"

private fun liquidBalanceUnitLabel(useSats: Boolean): String = if (useSats) "sats" else "BTC"

private fun liquidTransactionKindLabel(transaction: LiquidTransaction): String =
    when (transaction.source) {
        LiquidTxSource.CHAIN_SWAP -> "Swap"
        LiquidTxSource.LIGHTNING_RECEIVE_SWAP -> "Lightning Received"
        LiquidTxSource.LIGHTNING_SEND_SWAP -> "Lightning Sent"
        LiquidTxSource.NATIVE -> if (transaction.balanceSatoshi >= 0) "Received" else "Sent"
    }

@Composable
private fun LiquidTransactionFilterButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) tint.copy(alpha = 0.16f) else DarkSurfaceVariant)
                .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) tint else TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun transactionSourceDetail(transaction: LiquidTransaction): String =
    when (transaction.source) {
        LiquidTxSource.CHAIN_SWAP -> "BTC/L-BTC chain swap"
        LiquidTxSource.LIGHTNING_RECEIVE_SWAP -> "Boltz Lightning receive swap"
        LiquidTxSource.LIGHTNING_SEND_SWAP -> "Boltz Lightning send swap"
        LiquidTxSource.NATIVE -> if (transaction.balanceSatoshi >= 0) {
            "Native L-BTC receive"
        } else {
            "Native L-BTC send"
        }
    }

private fun truncateMiddle(value: String, maxLength: Int): String {
    if (value.length <= maxLength || maxLength < 8) return value
    val keep = (maxLength - 3) / 2
    val tailKeep = maxLength - 3 - keep
    return value.take(keep) + "..." + value.takeLast(tailKeep)
}

