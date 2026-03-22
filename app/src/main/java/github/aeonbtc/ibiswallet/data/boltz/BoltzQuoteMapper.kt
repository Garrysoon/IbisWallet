package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.BoltzPairInfo
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapQuote
import github.aeonbtc.ibiswallet.data.model.SwapService
import kotlin.math.ceil

internal fun buildBoltzQuoteFromPair(
    direction: SwapDirection,
    amountSats: Long,
    pair: BoltzPairInfo,
    estimatedTime: String,
    typicalBtcTxVsize: Int,
    defaultLiquidSwapFeeRate: Double,
    receiveAmountOverride: Long? = null,
    serviceFeeOverride: Long? = null,
): SwapQuote {
    val fees = pair.fees
    val percentageFee = serviceFeeOverride ?: ceil(amountSats * fees.percentage / 100.0).toLong()

    return when (direction) {
        SwapDirection.BTC_TO_LBTC -> {
            val btcNetworkFee = fees.userLockupFee
            val liquidNetworkFee = fees.userClaimFee
            val providerMinerFee = fees.serverMinerFee
            val receiveAmount =
                receiveAmountOverride ?: (
                    amountSats - percentageFee - providerMinerFee - liquidNetworkFee
                    ).coerceAtLeast(0L)
            SwapQuote(
                service = SwapService.BOLTZ,
                direction = direction,
                sendAmount = amountSats,
                receiveAmount = receiveAmount,
                serviceFee = percentageFee,
                btcNetworkFee = btcNetworkFee,
                liquidNetworkFee = liquidNetworkFee,
                providerMinerFee = providerMinerFee,
                btcFeeRate = btcNetworkFee.toDouble() / typicalBtcTxVsize.toDouble(),
                liquidFeeRate = defaultLiquidSwapFeeRate,
                minAmount = pair.limits.minimal,
                maxAmount = pair.limits.maximal,
                estimatedTime = estimatedTime,
            )
        }

        SwapDirection.LBTC_TO_BTC -> {
            val liquidNetworkFee = fees.userLockupFee
            val btcNetworkFee = fees.userClaimFee
            val providerMinerFee = fees.serverMinerFee
            val receiveAmount =
                receiveAmountOverride ?: (
                    amountSats - percentageFee - providerMinerFee - btcNetworkFee
                    ).coerceAtLeast(0L)
            SwapQuote(
                service = SwapService.BOLTZ,
                direction = direction,
                sendAmount = amountSats,
                receiveAmount = receiveAmount,
                serviceFee = percentageFee,
                btcNetworkFee = btcNetworkFee,
                liquidNetworkFee = liquidNetworkFee,
                providerMinerFee = providerMinerFee,
                btcFeeRate = btcNetworkFee.toDouble() / typicalBtcTxVsize.toDouble(),
                liquidFeeRate = defaultLiquidSwapFeeRate,
                minAmount = pair.limits.minimal,
                maxAmount = pair.limits.maximal,
                estimatedTime = estimatedTime,
            )
        }
    }
}
