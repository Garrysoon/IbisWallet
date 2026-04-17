package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.silentpayments.SilentPaymentConfig
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow

/**
 * Silent Payments settings screen.
 *
 * Configuration:
 * - Enable/disable Silent Payments
 * - Set scan server URL
 * - View activation block height
 * - Reset/clear Silent Payment data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentPaymentSettingsScreen(
    config: SilentPaymentConfig,
    onConfigChange: (SilentPaymentConfig) -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var serverUrl by remember { mutableStateOf(config.scanServerUrl ?: "") }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Silent Payments Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        containerColor = DarkBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Warning card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = WarningYellow.copy(alpha = 0.1f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningYellow,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(
                            text = "Experimental Feature",
                            color = WarningYellow,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "Silent Payments are still in development. Use testnet for testing.",
                            color = WarningYellow.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Enable toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Enable Silent Payments",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Generate reusable sp1... address",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BitcoinOrange,
                            checkedTrackColor = BitcoinOrange.copy(alpha = 0.5f),
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Server settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Scanning Server",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = " Server detects incoming payments. Only your public scan key is sent.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL (optional)") },
                        placeholder = { Text("Leave empty for stub (no scanning)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Supported: Blockstream Esplora, custom server",
                        color = TextSecondary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current config info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurface.copy(alpha = 0.5f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Current Configuration",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    InfoRow("Enabled", if (config.enabled) "Yes" else "No")
                    InfoRow("Network", if (config.useTestnetServer) "Testnet" else "Mainnet")
                    InfoRow("Server URL", config.scanServerUrl ?: "Stub (disabled)")
                    InfoRow("Activation Height", config.activationHeight?.toString() ?: "Not set")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Divider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = TextSecondary.copy(alpha = 0.2f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Reset section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ErrorRed.copy(alpha = 0.1f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Danger Zone",
                        color = ErrorRed,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Resetting will clear Silent Payment keys. " +
                               "You will lose access to any funds sent to your Silent Payment address " +
                               "unless you have a backup of your wallet seed phrase.",
                        color = ErrorRed.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (showResetConfirm) {
                        Text(
                            text = "Are you sure? This cannot be undone.",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            TextButton(
                                onClick = { showResetConfirm = false },
                            ) {
                                Text("Cancel", color = TextSecondary)
                            }

                            Spacer(modifier = Modifier.size(16.dp))

                            TextButton(
                                onClick = {
                                    onReset()
                                    showResetConfirm = false
                                },
                            ) {
                                Text("Yes, Reset", color = ErrorRed)
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { showResetConfirm = true },
                        ) {
                            Text("Reset Silent Payments", color = ErrorRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BitcoinOrange,
                ),
            ) {
                TextButton(
                    onClick = {
                        onConfigChange(
                            SilentPaymentConfig(
                                enabled = enabled,
                                scanServerUrl = serverUrl.takeIf { it.isNotBlank() },
                                activationHeight = config.activationHeight,
                                useTestnetServer = config.useTestnetServer,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Save Settings",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
