package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.silentpayments.SilentPaymentAddress
import github.aeonbtc.ibiswallet.silentpayments.SilentPaymentException
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrCode
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import kotlinx.coroutines.delay

/**
 * Silent Payment receive screen.
 *
 * Displays the static Silent Payment address (sp1...) which can be reused
 * for multiple payments without address reuse on the blockchain.
 *
 * Features:
 * - Large QR code for scanning
 * - Copy address button
 * - Educational info about Silent Payments
 * - Warning that it's experimental
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentPaymentReceiveScreen(
    viewModel: github.aeonbtc.ibiswallet.viewmodel.WalletViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var spAddress by remember { mutableStateOf<SilentPaymentAddress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }

    // Load Silent Payment address
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            // This would call viewModel.getSilentPaymentAddress() in real implementation
            // For now, simulate loading
            delay(500)

            // TODO: Replace with actual call
            // spAddress = viewModel.getSilentPaymentAddress()

            isLoading = false
        } catch (e: SilentPaymentException) {
            error = e.message
            isLoading = false
        } catch (e: Exception) {
            error = "Failed to load Silent Payment address"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Silent Payment (Experimental)") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Back",
                            tint = WarningYellow,
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Experimental warning card
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
                    Text(
                        text = "Experimental Feature: Silent Payments are still being tested. " +
                               "Use testnet for testing. Do not use for large amounts on mainnet.",
                        color = WarningYellow,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            when {
                isLoading -> {
                    Spacer(modifier = Modifier.height(64.dp))
                    CircularProgressIndicator(color = BitcoinOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Generating Silent Payment address...",
                        color = TextSecondary,
                    )
                }

                error != null -> {
                    Spacer(modifier = Modifier.height(64.dp))
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error!!,
                        color = ErrorRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                        ),
                    ) {
                        Text("Go Back")
                    }
                }

                spAddress != null -> {
                    // QR Code section
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // QR Code placeholder - in real implementation would use spAddress.address
                            Box(
                                modifier = Modifier
                                    .size(280.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                // TODO: Replace with actual QR code
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "QR Code",
                                    modifier = Modifier.size(200.dp),
                                    tint = Color.Black,
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Scan to pay via Silent Payment",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Address section
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Your Silent Payment Address",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Display address with word wrap
                            Text(
                                text = spAddress!!.address,
                                color = BitcoinOrange,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Copy button
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString(spAddress!!.address))
                                    copied = true
                                    // Reset after 2 seconds
                                    kotlinx.coroutines.GlobalScope.launch {
                                        delay(2000)
                                        copied = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (copied) SuccessGreen else BitcoinOrange,
                                ),
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Default.Info else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                                Text(if (copied) "Copied!" else "Copy Address")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Educational section
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
                                text = "What are Silent Payments?",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "• This address can be reused multiple times\n" +
                                       "• Each payment creates a unique on-chain address\n" +
                                       "• No address reuse visible on blockchain\n" +
                                       "• BIP352 protocol for enhanced privacy",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Scanning",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Payments are detected via server-assisted scanning. " +
                                       "The server only needs your public scan key - your private keys never leave the device.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                else -> {
                    // Silent Payments not initialized
                    Spacer(modifier = Modifier.height(64.dp))
                    Text(
                        text = "Silent Payments not initialized",
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            // TODO: Initialize Silent Payments
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                        ),
                    ) {
                        Text("Enable Silent Payments")
                    }
                }
            }
        }
    }
}

// Extension for GlobalScope - this is a temporary workaround
// In real implementation, use proper coroutine scope from ViewModel
private fun kotlinx.coroutines.GlobalScope.launch(
    block: suspend () -> Unit,
): kotlinx.coroutines.Job {
    return kotlinx.coroutines.GlobalScope.launch {
        block()
    }
}
