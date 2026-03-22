package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.data.model.LiquidTransaction
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.LiquidTxType
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Transaction list item for Liquid L-BTC transactions.
 * Follows the same visual pattern as Bitcoin transaction items
 * but with Liquid-specific source badges and L-BTC denomination.
 */
@Composable
fun LiquidTransactionItem(
    tx: LiquidTransaction,
    denomination: String,
    btcPrice: Double?,
    privacyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val swapDetails = tx.swapDetails?.takeIf { tx.source == LiquidTxSource.CHAIN_SWAP }
    val swapRole = swapDetails?.role
    val isReceive =
        when (swapRole) {
            LiquidSwapTxRole.FUNDING -> false
            LiquidSwapTxRole.SETTLEMENT -> true
            null -> tx.balanceSatoshi >= 0
        }
    val displayAmountSats =
        when (swapRole) {
            LiquidSwapTxRole.FUNDING -> swapDetails.sendAmountSats.takeIf { it > 0L } ?: abs(tx.balanceSatoshi)
            LiquidSwapTxRole.SETTLEMENT ->
                swapDetails.expectedReceiveAmountSats.takeIf { it > 0L } ?: abs(tx.balanceSatoshi)
            null -> abs(tx.balanceSatoshi)
        }
    val displayLabel = label?.takeIf { it.isNotBlank() } ?: tx.memo.takeIf { it.isNotBlank() }
    val icon = if (isReceive) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade
    val iconTint = if (isReceive) AccentGreen else AccentRed
    val iconBackground = if (isReceive) AccentGreen.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f)
    val amountColor = if (isReceive) AccentGreen else AccentRed
    val networkBadge = networkBadge(tx.source)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (isReceive) "Received" else "Sent",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }


            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = defaultTransactionTitle(tx, isReceive),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(networkBadge.color.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    {
                        Text(
                            text = networkBadge.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = networkBadge.color,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                if (!displayLabel.isNullOrBlank()) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = AccentTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = tx.timestamp?.let { formatTimestamp(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = TextSecondary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val amountText = if (privacyMode) {
                    "****"
                } else {
                    val prefix = if (isReceive) "+" else "-"
                    if (denomination == "SATS") {
                        "$prefix${"%,d".format(displayAmountSats)} sats"
                    } else {
                        "$prefix${"%.8f".format(displayAmountSats / 100_000_000.0)}"
                    }
                }

                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    textAlign = TextAlign.End,
                )

                Text(
                    text = if (!privacyMode && btcPrice != null) {
                        "$%.2f".format(displayAmountSats / 100_000_000.0 * btcPrice)
                    } else {
                        ""
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    textAlign = TextAlign.End,
                )
                if (tx.height == null) {
                    StatusBadge(
                        label = "Pending",
                        color = LiquidTeal,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

private fun defaultTransactionTitle(
    tx: LiquidTransaction,
    isReceive: Boolean,
): String =
    when {
        tx.source == LiquidTxSource.CHAIN_SWAP -> if (isReceive) "Received" else "Sent"
        tx.balanceSatoshi >= 0 -> "Received"
        tx.type == LiquidTxType.SEND -> "Sent"
        else -> if (isReceive) "Received" else "Sent"
    }

private data class TransactionNetworkBadge(
    val label: String,
    val color: Color,
)

private fun networkBadge(source: LiquidTxSource): TransactionNetworkBadge =
    when (source) {
        LiquidTxSource.CHAIN_SWAP -> TransactionNetworkBadge(
            label = "Swap",
            color = BitcoinOrange,
        )
        LiquidTxSource.LIGHTNING_RECEIVE_SWAP,
        LiquidTxSource.LIGHTNING_SEND_SWAP,
        -> TransactionNetworkBadge(
            label = "Lightning",
            color = LightningYellow,
        )
        LiquidTxSource.NATIVE -> TransactionNetworkBadge(
            label = "Liquid",
            color = LiquidTeal,
        )
    }

private fun formatTimestamp(epochSeconds: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
        sdf.format(Date(epochSeconds * 1000))
    } catch (_: Exception) { "" }
}
