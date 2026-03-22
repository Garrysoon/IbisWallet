@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentDenomination: String = SecureStorage.DENOMINATION_BTC,
    onDenominationChange: (String) -> Unit = {},
    spendUnconfirmed: Boolean = true,
    onSpendUnconfirmedChange: (Boolean) -> Unit = {},
    walletNotificationsEnabled: Boolean = false,
    onWalletNotificationsEnabledChange: (Boolean) -> Unit = {},
    nfcEnabled: Boolean = true,
    onNfcEnabledChange: (Boolean) -> Unit = {},
    hasNfcHardware: Boolean = false,
    isSystemNfcEnabled: Boolean = false,
    supportsNfcBroadcast: Boolean = false,
    currentFeeSource: String = SecureStorage.FEE_SOURCE_OFF,
    onFeeSourceChange: (String) -> Unit = {},
    customFeeSourceUrl: String = "",
    onCustomFeeSourceUrlSave: (String) -> Unit = {},
    currentPriceSource: String = SecureStorage.PRICE_SOURCE_OFF,
    onPriceSourceChange: (String) -> Unit = {},
    currentMempoolServer: String = SecureStorage.MEMPOOL_SPACE,
    onMempoolServerChange: (String) -> Unit = {},
    customMempoolUrl: String = "",
    onCustomMempoolUrlSave: (String) -> Unit = {},
    currentSwipeMode: String = SecureStorage.SWIPE_MODE_DISABLED,
    onSwipeModeChange: (String) -> Unit = {},
    isLiquidAvailable: Boolean = false,
    torStatus: TorStatus = TorStatus.DISCONNECTED,
    onOpenBitcoinElectrum: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Card 1: Appearance & Display ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    text = "Display",
                    style = MaterialTheme.typography.titleMedium,
                    color = BitcoinOrange,
                )

                Spacer(modifier = Modifier.height(12.dp))

                val isSats = currentDenomination == SecureStorage.DENOMINATION_SATS
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onDenominationChange(
                                    if (!isSats) {
                                        SecureStorage.DENOMINATION_SATS
                                    } else {
                                        SecureStorage.DENOMINATION_BTC
                                    },
                                )
                            },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CurrencyBitcoin,
                            contentDescription = null,
                            tint = BitcoinOrange,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ToggleOptionText(
                            title = if (isSats) "Sats" else "BTC",
                            subtitle = if (isSats) {
                                "Wallet amounts shown in satoshis"
                            } else {
                                "Wallet amounts shown in bitcoin"
                            },
                        )
                    }
                    SquareToggle(
                        checked = isSats,
                        onCheckedChange = { useSats ->
                            onDenominationChange(
                                if (useSats) {
                                    SecureStorage.DENOMINATION_SATS
                                } else {
                                    SecureStorage.DENOMINATION_BTC
                                },
                            )
                        },
                        checkedColor = TextSecondary,
                        uncheckedColor = TextSecondary.copy(alpha = 0.3f),
                        uncheckedBorderColor = TextSecondary,
                        uncheckedThumbColor = TextSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Swipe Navigation",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                SwipeModeDropdown(
                    currentMode = currentSwipeMode,
                    onModeSelected = onSwipeModeChange,
                    isLiquidAvailable = isLiquidAvailable,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Card 2: Transaction Settings ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = BitcoinOrange,
                )

                Spacer(modifier = Modifier.height(12.dp))

                val nfcSubtitle =
                    when {
                        !hasNfcHardware -> "Not available on this device"
                        !isSystemNfcEnabled -> "Turn on NFC in Android settings"
                        !supportsNfcBroadcast -> "Enable NFC reading (broadcast unsupported)"
                        else -> "Enable NFC reading and broadcast"
                    }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSpendUnconfirmedChange(!spendUnconfirmed) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = BitcoinOrange,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ToggleOptionText(
                            title = "Spend Unconfirmed",
                            subtitle = "Allow spending unconfirmed UTXOs",
                        )
                    }
                    SquareToggle(
                        checked = spendUnconfirmed,
                        onCheckedChange = onSpendUnconfirmedChange,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onWalletNotificationsEnabledChange(!walletNotificationsEnabled) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = BitcoinOrange,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ToggleOptionText(
                            title = "Push Notifications",
                            subtitle = "Notifications for wallet activity",
                        )
                    }
                    SquareToggle(
                        checked = walletNotificationsEnabled,
                        onCheckedChange = onWalletNotificationsEnabledChange,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (hasNfcHardware) {
                                    Modifier.clickable { onNfcEnabledChange(!nfcEnabled) }
                                } else {
                                    Modifier
                                },
                            ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = if (hasNfcHardware) BitcoinOrange else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ToggleOptionText(
                            title = "NFC",
                            subtitle = nfcSubtitle,
                            titleColor = if (hasNfcHardware) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                TextSecondary.copy(alpha = 0.4f)
                            },
                            subtitleColor = if (hasNfcHardware) TextSecondary else TextSecondary.copy(alpha = 0.4f),
                        )
                    }
                    SquareToggle(
                        checked = if (hasNfcHardware) nfcEnabled else false,
                        onCheckedChange = if (hasNfcHardware) onNfcEnabledChange else { _ -> },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Card 3: External Services ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    text = "External Services",
                    style = MaterialTheme.typography.titleMedium,
                    color = BitcoinOrange,
                )

                Spacer(modifier = Modifier.height(12.dp))

            // Fee Rate Source
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fee Rate Source",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            FeeSourceDropdown(
                currentSource = currentFeeSource,
                onSourceSelected = onFeeSourceChange,
            )

            if (currentFeeSource == SecureStorage.FEE_SOURCE_MEMPOOL_ONION) {
                TorStatusIndicator(
                    torStatus = torStatus,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                )
            }

            if (currentFeeSource == SecureStorage.FEE_SOURCE_CUSTOM) {
                Spacer(modifier = Modifier.height(6.dp))

                var feeUrlDraft by remember(customFeeSourceUrl) {
                    mutableStateOf(customFeeSourceUrl)
                }
                var feeUrlError by remember { mutableStateOf<String?>(null) }
                var feeUrlSaved by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(feeUrlSaved) {
                    if (feeUrlSaved != null) {
                        delay(3000)
                        feeUrlSaved = null
                    }
                }

                val isOnionUrl =
                    try {
                        java.net.URI(feeUrlDraft).host?.endsWith(".onion") == true
                    } catch (_: Exception) {
                        feeUrlDraft.endsWith(".onion")
                    }
                val torStatusColor =
                    when (torStatus) {
                        TorStatus.CONNECTED -> SuccessGreen
                        TorStatus.CONNECTING, TorStatus.STARTING -> SuccessGreen.copy(alpha = 0.6f)
                        TorStatus.ERROR -> ErrorRed
                        TorStatus.DISCONNECTED -> TextSecondary
                    }
                val torStatusText =
                    when (torStatus) {
                        TorStatus.CONNECTED -> "Tor connected"
                        TorStatus.CONNECTING -> "Tor connecting..."
                        TorStatus.STARTING -> "Tor starting..."
                        TorStatus.ERROR -> "Tor error"
                        TorStatus.DISCONNECTED -> "Tor will start automatically"
                    }

                CompactTextFieldWithSave(
                    value = feeUrlDraft,
                    onValueChange = {
                        feeUrlDraft = it
                        feeUrlError = null
                        feeUrlSaved = null
                    },
                    onSave = {
                        val error = validateServerUrl(feeUrlDraft)
                        if (error != null) {
                            feeUrlError = error
                            feeUrlSaved = null
                        } else {
                            feeUrlError = null
                            onCustomFeeSourceUrlSave(feeUrlDraft)
                            feeUrlSaved = "Server saved"
                        }
                    },
                    placeholder = "http://192.168... or http://...onion",
                    errorMessage = feeUrlError,
                    successMessage = feeUrlSaved,
                    torStatusText = if (isOnionUrl) torStatusText else null,
                    torStatusColor = if (isOnionUrl) torStatusColor else null,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Block Explorer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Block Explorer",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            MempoolServerDropdown(
                currentServer = currentMempoolServer,
                onServerSelected = onMempoolServerChange,
            )

            if (currentMempoolServer == SecureStorage.MEMPOOL_CUSTOM) {
                Spacer(modifier = Modifier.height(6.dp))

                var mempoolUrlDraft by remember(customMempoolUrl) {
                    mutableStateOf(customMempoolUrl)
                }
                var mempoolUrlError by remember { mutableStateOf<String?>(null) }
                var mempoolUrlSaved by remember { mutableStateOf<String?>(null) }

                CompactTextFieldWithSave(
                    value = mempoolUrlDraft,
                    onValueChange = {
                        mempoolUrlDraft = it
                        mempoolUrlError = null
                        mempoolUrlSaved = null
                    },
                    onSave = {
                        val error = validateServerUrl(mempoolUrlDraft)
                        if (error != null) {
                            mempoolUrlError = error
                            mempoolUrlSaved = null
                        } else {
                            mempoolUrlError = null
                            onCustomMempoolUrlSave(mempoolUrlDraft)
                            mempoolUrlSaved = "Server saved"
                        }
                    },
                    placeholder = "http://192.168... or http://...onion",
                    errorMessage = mempoolUrlError,
                    successMessage = mempoolUrlSaved,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BTC/USD Price Source
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "USD Price Source",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            PriceSourceDropdown(
                currentSource = currentPriceSource,
                onSourceSelected = onPriceSourceChange,
            )

            if (currentPriceSource == SecureStorage.PRICE_SOURCE_MEMPOOL_ONION) {
                TorStatusIndicator(
                    torStatus = torStatus,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = BorderColor.copy(alpha = 0.7f))

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bitcoin Electrum Server",
                    style = TextStyle(fontSize = 15.sp),
                    color = TextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenBitcoinElectrum),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open Bitcoin Electrum settings",
                        tint = BitcoinOrange,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Layer2OptionsScreen(
    layer2Enabled: Boolean = false,
    onLayer2EnabledChange: (Boolean) -> Unit = {},
    currentBoltzApiSource: String = SecureStorage.BOLTZ_API_DISABLED,
    onBoltzApiSourceChange: (String) -> Unit = {},
    currentSideSwapApiSource: String = SecureStorage.SIDESWAP_API_DISABLED,
    onSideSwapApiSourceChange: (String) -> Unit = {},
    currentLiquidExplorer: String = SecureStorage.LIQUID_EXPLORER_DISABLED,
    onLiquidExplorerChange: (String) -> Unit = {},
    customLiquidExplorerUrl: String = "",
    onCustomLiquidExplorerUrlSave: (String) -> Unit = {},
    layer2TorStatus: TorStatus = TorStatus.DISCONNECTED,
    onOpenLiquidElectrum: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Layer 2",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Layer2OptionsCard(
            layer2Enabled = layer2Enabled,
            onLayer2EnabledChange = onLayer2EnabledChange,
        )

        if (layer2Enabled) {
            Spacer(modifier = Modifier.height(8.dp))

            Layer2ExternalServicesCard(
                currentBoltzApiSource = currentBoltzApiSource,
                onBoltzApiSourceChange = onBoltzApiSourceChange,
                currentSideSwapApiSource = currentSideSwapApiSource,
                onSideSwapApiSourceChange = onSideSwapApiSourceChange,
                currentLiquidExplorer = currentLiquidExplorer,
                onLiquidExplorerChange = onLiquidExplorerChange,
                customLiquidExplorerUrl = customLiquidExplorerUrl,
                onCustomLiquidExplorerUrlSave = onCustomLiquidExplorerUrlSave,
                layer2TorStatus = layer2TorStatus,
                onOpenLiquidElectrum = onOpenLiquidElectrum,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun Layer2OptionsCard(
    layer2Enabled: Boolean,
    onLayer2EnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = "Layer 2 Options",
                style = MaterialTheme.typography.titleMedium,
                color = BitcoinOrange,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleOptionText(
                    title = "Liquid w/Lightning swaps",
                    subtitle = "Trusted federation sidechain",
                    titleColor = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                SquareToggle(
                    checked = layer2Enabled,
                    onCheckedChange = onLayer2EnabledChange,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleOptionText(
                    title = "Ark w/Lightning swaps",
                    subtitle = "Trustless L2 protocol (Coming soon)",
                    titleColor = TextTertiary,
                    subtitleColor = TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                SquareToggle(
                    checked = false,
                    onCheckedChange = { /* disabled */ },
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun Layer2ExternalServicesCard(
    currentBoltzApiSource: String,
    onBoltzApiSourceChange: (String) -> Unit,
    currentSideSwapApiSource: String,
    onSideSwapApiSourceChange: (String) -> Unit,
    currentLiquidExplorer: String,
    onLiquidExplorerChange: (String) -> Unit,
    customLiquidExplorerUrl: String,
    onCustomLiquidExplorerUrlSave: (String) -> Unit,
    layer2TorStatus: TorStatus,
    onOpenLiquidElectrum: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = "External Services",
                style = MaterialTheme.typography.titleMedium,
                color = BitcoinOrange,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Boltz API",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            BoltzApiSourceDropdown(
                currentSource = currentBoltzApiSource,
                onSourceSelected = onBoltzApiSourceChange,
            )

            if (currentBoltzApiSource == SecureStorage.BOLTZ_API_TOR) {
                TorStatusIndicator(
                    torStatus = layer2TorStatus,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SideSwap API",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SideSwapApiSourceDropdown(
                currentSource = currentSideSwapApiSource,
                onSourceSelected = onSideSwapApiSourceChange,
            )

            if (currentSideSwapApiSource == SecureStorage.SIDESWAP_API_TOR) {
                TorStatusIndicator(
                    torStatus = layer2TorStatus,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Block Explorer",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LiquidExplorerDropdown(
                currentExplorer = currentLiquidExplorer,
                onExplorerSelected = onLiquidExplorerChange,
            )

            if (currentLiquidExplorer == SecureStorage.LIQUID_EXPLORER_CUSTOM) {
                Spacer(modifier = Modifier.height(6.dp))

                var liquidExplorerUrlDraft by remember(customLiquidExplorerUrl) {
                    mutableStateOf(customLiquidExplorerUrl)
                }
                var liquidExplorerUrlError by remember { mutableStateOf<String?>(null) }
                var liquidExplorerUrlSaved by remember { mutableStateOf<String?>(null) }

                CompactTextFieldWithSave(
                    value = liquidExplorerUrlDraft,
                    onValueChange = {
                        liquidExplorerUrlDraft = it
                        liquidExplorerUrlError = null
                        liquidExplorerUrlSaved = null
                    },
                    onSave = {
                        val error = validateServerUrl(liquidExplorerUrlDraft)
                        if (error != null) {
                            liquidExplorerUrlError = error
                            liquidExplorerUrlSaved = null
                        } else {
                            liquidExplorerUrlError = null
                            onCustomLiquidExplorerUrlSave(liquidExplorerUrlDraft)
                            liquidExplorerUrlSaved = "Server saved"
                        }
                    },
                    placeholder = "http://192.168... or http://...onion",
                    errorMessage = liquidExplorerUrlError,
                    successMessage = liquidExplorerUrlSaved,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = BorderColor.copy(alpha = 0.7f))

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Liquid Electrum Server",
                    style = TextStyle(fontSize = 15.sp),
                    color = TextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenLiquidElectrum),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open Liquid Electrum settings",
                        tint = BitcoinOrange,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Data class for mempool server options
 */
private data class MempoolServerOption(
    val id: String,
    val name: String,
    val description: String,
)

/**
 * Dropdown for selecting mempool server
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MempoolServerDropdown(
    currentServer: String,
    onServerSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val serverOptions =
        listOf(
            MempoolServerOption(
                id = SecureStorage.MEMPOOL_DISABLED,
                name = "Disabled",
                description = "No Liquid explorer links",
            ),
            MempoolServerOption(
                id = SecureStorage.MEMPOOL_SPACE,
                name = "mempool.space",
                description = "Clearnet",
            ),
            MempoolServerOption(
                id = SecureStorage.MEMPOOL_ONION,
                name = "mempool.space (Onion)",
                description = "Onion via Tor Browser",
            ),
            MempoolServerOption(
                id = SecureStorage.MEMPOOL_CUSTOM,
                name = "Custom Server",
                description = "Custom URL",
            ),
        )

    val selectedOption = serverOptions.find { it.id == currentServer } ?: serverOptions.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            serverOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentServer,
                        )
                    },
                    onClick = {
                        onServerSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentServer) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

private data class SwipeModeOption(
    val id: String,
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeModeDropdown(
    currentMode: String,
    onModeSelected: (String) -> Unit,
    isLiquidAvailable: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    val options = buildList {
        add(
            SwipeModeOption(
                id = SecureStorage.SWIPE_MODE_DISABLED,
                name = "Disabled",
                description = "No swipe gestures",
            ),
        )
        add(
            SwipeModeOption(
                id = SecureStorage.SWIPE_MODE_WALLETS,
                name = "Wallets",
                description = "Swipe between wallets",
            ),
        )
        add(
            SwipeModeOption(
                id = SecureStorage.SWIPE_MODE_SEND_RECEIVE,
                name = "Send / Receive",
                description = "Swipe between balance, send, and receive",
            ),
        )
        if (isLiquidAvailable) {
            add(
                SwipeModeOption(
                    id = SecureStorage.SWIPE_MODE_LAYERS,
                    name = "Layers",
                    description = "Swipe between layer 1 and layer 2",
                ),
            )
        }
    }

    val selectedOption = options.find { it.id == currentMode } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentMode,
                        )
                    },
                    onClick = {
                        onModeSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentMode) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Data class for fee source options
 */
private data class FeeSourceOption(
    val id: String,
    val name: String,
    val description: String,
)

/**
 * Dropdown for selecting fee estimation source
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeeSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val sourceOptions =
        listOf(
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_OFF,
                name = "Disabled",
                description = "No fee rate fetching",
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_MEMPOOL,
                name = "mempool.space",
                description = "Clearnet",
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_MEMPOOL_ONION,
                name = "mempool.space (Onion)",
                description = "Onion via Tor",
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_ELECTRUM,
                name = "Electrum Server",
                description = "Connected server",
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_CUSTOM,
                name = "Custom Server",
                description = "Custom URL",
            ),
        )

    val selectedOption = sourceOptions.find { it.id == currentSource } ?: sourceOptions.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            sourceOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentSource,
                        )
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Data class for price source options
 */
private data class PriceSourceOption(
    val id: String,
    val name: String,
    val description: String,
)

/**
 * Dropdown for selecting BTC/USD price source
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val sourceOptions =
        listOf(
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_OFF,
                name = "Disabled",
                description = "No USD prices",
            ),
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_MEMPOOL,
                name = "mempool.space",
                description = "Clearnet",
            ),
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_MEMPOOL_ONION,
                name = "mempool.space (Onion)",
                description = "Onion via Tor",
            ),
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_COINGECKO,
                name = "CoinGecko",
                description = "Clearnet",
            ),
        )

    val selectedOption = sourceOptions.find { it.id == currentSource } ?: sourceOptions.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            sourceOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentSource,
                        )
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

private data class Layer2ApiSourceOption(
    val id: String,
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoltzApiSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            Layer2ApiSourceOption(
                id = SecureStorage.BOLTZ_API_DISABLED,
                name = "Disabled",
                description = "No Boltz swaps or lightning functionality",
            ),
            Layer2ApiSourceOption(
                id = SecureStorage.BOLTZ_API_CLEARNET,
                name = "Boltz",
                description = "Clearnet",
            ),
            Layer2ApiSourceOption(
                id = SecureStorage.BOLTZ_API_TOR,
                name = "Boltz (Onion)",
                description = "Onion via Tor",
            ),
        )
    val selectedOption = options.find { it.id == currentSource } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(true).background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentSource,
                        )
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideSwapApiSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            Layer2ApiSourceOption(
                id = SecureStorage.SIDESWAP_API_DISABLED,
                name = "Disabled",
                description = "No SideSwap swaps",
            ),
            Layer2ApiSourceOption(
                id = SecureStorage.SIDESWAP_API_CLEARNET,
                name = "SideSwap",
                description = "Clearnet",
            ),
            Layer2ApiSourceOption(
                id = SecureStorage.SIDESWAP_API_TOR,
                name = "SideSwap (Tor)",
                description = "Clearnet via Tor",
            ),
        )
    val selectedOption = options.find { it.id == currentSource } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(true).background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentSource,
                        )
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

private data class LiquidExplorerOption(
    val id: String,
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidExplorerDropdown(
    currentExplorer: String,
    onExplorerSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_DISABLED,
                name = "Disabled",
                description = "No block explorer links",
            ),
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_LIQUID_NETWORK,
                name = "liquid.network",
                description = "Clearnet",
            ),
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_LIQUID_NETWORK_ONION,
                name = "liquid.network (Onion)",
                description = "Onion via Tor Browser",
            ),
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_BLOCKSTREAM,
                name = "Blockstream",
                description = "Clearnet",
            ),
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_CUSTOM,
                name = "Custom Server",
                description = "Custom URL",
            ),
        )
    val selectedOption = options.find { it.id == currentExplorer } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(true).background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentExplorer,
                        )
                    },
                    onClick = {
                        onExplorerSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentExplorer) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Compact read-only dropdown field with proper text alignment (no clipping).
 */
@Composable
private fun CompactDropdownField(
    value: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, if (expanded) BitcoinOrange else BorderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            style = TextStyle(fontSize = 14.5.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DropdownOptionText(
    title: String,
    subtitle: String,
    selected: Boolean,
) {
    Column {
        Text(
            text = title,
            style = TextStyle(fontSize = 14.5.sp),
            color = if (selected) BitcoinOrange else MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = TextStyle(fontSize = 12.5.sp),
            color = TextSecondary,
        )
    }
}

@Composable
private fun ToggleOptionText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    subtitleColor: androidx.compose.ui.graphics.Color = TextSecondary,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = TextStyle(fontSize = 15.sp),
            color = titleColor,
        )
        Text(
            text = subtitle,
            style = TextStyle(fontSize = 13.sp),
            color = subtitleColor,
        )
    }
}

/**
 * Validates a server URL. Returns an error message if invalid, null if valid.
 */
private fun validateServerUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return "URL cannot be empty"
    val lower = trimmed.lowercase()
    val hasScheme = lower.startsWith("http://") || lower.startsWith("https://")
    if (!hasScheme) return "URL must start with http:// or https://"
    return null
}

/**
 * Compact editable text field with a right-aligned Save button and optional error message.
 */
/**
 * Tor connection status indicator — colored dot + status text.
 * Reused for any onion-based source (fee, price, custom URL).
 */
@Composable
private fun TorStatusIndicator(
    torStatus: TorStatus,
    modifier: Modifier = Modifier,
) {
    val torStatusColor =
        when (torStatus) {
            TorStatus.CONNECTED -> SuccessGreen
            TorStatus.CONNECTING, TorStatus.STARTING -> SuccessGreen.copy(alpha = 0.6f)
            TorStatus.ERROR -> ErrorRed
            TorStatus.DISCONNECTED -> TextSecondary
        }
    val torStatusText =
        when (torStatus) {
            TorStatus.CONNECTED -> "Tor connected"
            TorStatus.CONNECTING -> "Tor connecting..."
            TorStatus.STARTING -> "Tor starting..."
            TorStatus.ERROR -> "Tor error"
            TorStatus.DISCONNECTED -> "Tor will start automatically"
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(torStatusColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = torStatusText,
            style = MaterialTheme.typography.bodySmall,
            color = torStatusColor,
        )
    }
}

@Composable
private fun CompactTextFieldWithSave(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    successMessage: String? = null,
    torStatusText: String? = null,
    torStatusColor: androidx.compose.ui.graphics.Color? = null,
) {
    val borderColor =
        when {
            errorMessage != null -> ErrorRed
            successMessage != null -> SuccessGreen
            else -> BorderColor
        }

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle =
                    TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                cursorBrush = SolidColor(BitcoinOrange),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(fontSize = 13.sp),
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    }
                },
            )

            Text(
                text = if (successMessage != null) "Saved" else "Save",
                style = TextStyle(fontSize = 13.sp),
                color = if (successMessage != null) SuccessGreen else BitcoinOrange,
                modifier =
                    Modifier
                        .clickable(onClick = onSave)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }

        // Error and/or Tor status on the same line
        if (errorMessage != null || torStatusText != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (torStatusText != null && torStatusColor != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(torStatusColor),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = torStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = torStatusColor,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
