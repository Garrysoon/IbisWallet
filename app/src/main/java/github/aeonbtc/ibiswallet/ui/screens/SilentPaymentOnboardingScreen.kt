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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow

/**
 * Silent Payments Onboarding Screen.
 *
 * Educational screen explaining what Silent Payments are,
 * how they work, and privacy benefits.
 *
 * Shown on first use or when user clicks "Learn More".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentPaymentOnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 4

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Silent Payments") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                ),
                actions = {
                    TextButton(onClick = onSkip) {
                        Text("Skip", color = TextSecondary)
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Page content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (currentPage) {
                    0 -> IntroPage()
                    1 -> PrivacyPage()
                    2 -> HowItWorksPage()
                    3 -> GetStartedPage(onComplete)
                }
            }

            // Progress indicator
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(totalPages) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index == currentPage) BitcoinOrange
                                else TextSecondary.copy(alpha = 0.3f)
                            )
                            .padding(horizontal = 4.dp)
                    )
                    if (index < totalPages - 1) {
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button
                if (currentPage > 0) {
                    IconButton(
                        onClick = { currentPage-- },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous",
                            tint = TextPrimary,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Next/Complete button
                if (currentPage < totalPages - 1) {
                    Button(
                        onClick = { currentPage++ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                        ),
                    ) {
                        Text("Next")
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen,
                        ),
                    ) {
                        Text("Enable Silent Payments")
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(60.dp))
                .background(BitcoinOrange.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = BitcoinOrange,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Silent Payments",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "A new way to receive Bitcoin with enhanced privacy",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = DarkSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                FeatureItem(
                    icon = Icons.Default.QrCode,
                    title = "One Address, Infinite Payments",
                    description = "Share a single static address. Each payment creates a unique on-chain address.",
                )

                Spacer(modifier = Modifier.height(12.dp))

                FeatureItem(
                    icon = Icons.Default.Lock,
                    title = "No Address Reuse",
                    description = "Blockchain observers cannot link your payments together.",
                )

                Spacer(modifier = Modifier.height(12.dp))

                FeatureItem(
                    icon = Icons.Default.Notifications,
                    title = "Automatic Detection",
                    description = "Your wallet automatically finds payments without you doing anything.",
                )
            }
        }
    }
}

@Composable
private fun PrivacyPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Privacy Benefits",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = DarkSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = "Traditional Bitcoin",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• New address needed for each payment\n" +
                           "• Address reuse harms privacy\n" +
                           "• Managing many addresses is hard",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TextSecondary.copy(alpha = 0.3f)),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "With Silent Payments",
                    color = SuccessGreen,
                    style = MaterialTheme.typography.labelLarge,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• One reusable address (sp1...)\n" +
                           "• Each payment uses unique on-chain address\n" +
                           "• No visible link between payments\n" +
                           "• Simple for you, private by design",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HowItWorksPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "How It Works",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        StepCard(
            number = 1,
            title = "Your Static Address",
            description = "You share your sp1... address (like a phone number). It's always the same.",
        )

        Spacer(modifier = Modifier.height(12.dp))

        StepCard(
            number = 2,
            title = "Sender Creates Unique Address",
            description = "When someone pays you, their wallet generates a one-time Taproot address specifically for this payment.",
        )

        Spacer(modifier = Modifier.height(12.dp))

        StepCard(
            number = 3,
            title = "Automatic Detection",
            description = "Your wallet scans the blockchain and automatically finds these payments using your scan key.",
        )

        Spacer(modifier = Modifier.height(12.dp))

        StepCard(
            number = 4,
            title = "You Can Spend",
            description = "Your wallet derives the private key for each payment, so you can spend the funds normally.",
        )
    }
}

@Composable
private fun GetStartedPage(onEnable: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ready to Start?",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = WarningYellow.copy(alpha = 0.1f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
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
                        text = "Silent Payments are new. Start with testnet and small amounts.",
                        color = WarningYellow.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = DarkSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = "What Happens When You Enable:",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "✓ New scan & spend keys are generated\n" +
                           "✓ Your sp1... address is created\n" +
                           "✓ Wallet starts scanning for payments\n" +
                           "✓ Keys are backed up with your wallet seed",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BitcoinOrange,
            modifier = Modifier.size(24.dp),
        )

        Column(
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BitcoinOrange),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Column(
                modifier = Modifier.padding(start = 16.dp),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = description,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
