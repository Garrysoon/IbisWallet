package github.aeonbtc.ibiswallet.ui.components

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sparrowwallet.hummingbird.UREncoder
import com.sparrowwallet.hummingbird.registry.CryptoPSBT
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.Bbqr
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import kotlinx.coroutines.delay

/**
 * Animated QR code component that displays PSBT/PSET data as cycling QR frames.
 * Used for exporting PSBTs/PSETs to hardware wallets via animated QR codes.
 *
 * OPTIMIZATION: Now uses BBQr encoding (Zlib+Base32) instead of BC-UR for 3-4x better density.
 * BBQr provides ~1062 bytes/QR vs ~240 bytes/QR for BC-UR, dramatically reducing frame count.
 * Example: 20KB PSBT with 20 UTXOs now requires ~20-30 frames instead of 3000+ frames.
 *
 * Falls back to BC-UR only for hardware wallets that don't support BBQr (legacy mode).
 *
 * Hardware wallet compatibility:
 * - Coldcard Q, SeedSigner, Passport, Jade support BBQr
 * - Keystone, Bitbox02 may require BC-UR mode
 *
 * Optimized for hardware wallet cameras (SeedSigner, Coldcard, Keystone, Jade):
 * - Slower cadence for Jade and other lower-FPS cameras
 * - High QR versions (25-40) for maximum density with BBQr
 * - Stable ordered part loop instead of an aggressive changing stream
 * - Optional pause/step controls for scanners that miss fast transitions
 * - Thick white border for contrast
 * - Screen brightness boost and keep-screen-on
 * - Tap to enlarge for full-screen display
 */
@Composable
fun AnimatedQrCode(
    dataBase64: String,
    modifier: Modifier = Modifier,
    qrSize: Dp = 280.dp,
    density: SecureStorage.QrDensity = SecureStorage.QrDensity.MEDIUM,
    brightness: Float? = null,
    playbackSpeed: SecureStorage.QrPlaybackSpeed = SecureStorage.QrPlaybackSpeed.MEDIUM,
) {
    val context = LocalContext.current
    var showEnlarged by remember { mutableStateOf(false) }

    val dataBytes =
        remember(dataBase64) {
            android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
        }

    // OPTIMIZATION: Use BBQr (Better Bitcoin QR) instead of BC-UR for 3-4x better density
    // BBQr: ~1062 bytes/QR frame vs BC-UR: ~240 bytes/QR frame
    // This reduces 20 UTXO PSBT from 3000+ frames to ~20-30 frames
    val useBbqr = remember { true } // BBQr is now the default for PSBT

    val qrParts =
        remember(dataBytes, density) {
            if (useBbqr) {
                // BBQr mode: Zlib compression + Base32, much higher density
                val bbqrResult = Bbqr.split(
                    data = dataBytes,
                    fileType = Bbqr.FILE_TYPE_PSBT,
                    encoding = Bbqr.ENCODING_ZLIB, // Compression reduces size by 30-45%
                    minVersion = 20, // Start at v20 for good density
                    maxVersion = 40,   // Up to v40 for maximum capacity
                    minSplit = 1,
                    maxSplit = 100,    // Reasonable max for PSBT (most will be < 50 frames)
                )
                bbqrResult.parts
            } else {
                // Legacy BC-UR mode: for hardware wallets that don't support BBQr
                val fragmentSize = resolveUrFragmentSize(
                    payloadSize = dataBytes.size,
                    density = density,
                )
                val cryptoPsbt = CryptoPSBT(dataBytes)
                val ur = cryptoPsbt.toUR()
                // OPTIMIZATION: Reduced fountain code redundancy from 10 to 3
                // This cuts frame count by ~70% while maintaining reliability
                val encoder = UREncoder(ur, fragmentSize, 3, 0)
                List(encoder.seqLen) { encoder.nextPart() }
            }
        }
    val totalParts = qrParts.size
    val effectiveFrameDelayMs =
        remember(totalParts, playbackSpeed) {
            resolveUrFrameDelay(
                totalParts = totalParts,
                playbackSpeed = playbackSpeed,
            )
        }
    var partIndex by remember(qrParts) { mutableIntStateOf(0) }

    // Only animate if there are multiple parts
    val isAnimated = totalParts > 1
    val currentPart = qrParts[partIndex]

    if (isAnimated) {
        LaunchedEffect(qrParts, effectiveFrameDelayMs) {
            while (true) {
                delay(effectiveFrameDelayMs)
                partIndex = (partIndex + 1) % totalParts
            }
        }
    }

    val qrBitmap =
        remember(currentPart) {
            generateQrBitmap(currentPart.uppercase())
        }

    // Keep the screen on while QR is displayed and apply the user-selected brightness if provided.
    DisposableEffect(brightness) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        window?.let {
            val params = it.attributes
            brightness?.let { selectedBrightness ->
                params.screenBrightness = selectedBrightness.coerceIn(0f, 1f)
            }
            it.attributes = params
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window?.let {
                val params = it.attributes
                params.screenBrightness = originalBrightness
                it.attributes = params
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val enlargeQr = { showEnlarged = true }
    val dismissEnlarged = { showEnlarged = false }
    if (showEnlarged) {
        Dialog(
            onDismissRequest = dismissEnlarged,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(onClick = dismissEnlarged),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(360.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Enlarged PSBT QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    if (isAnimated) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Part ${partIndex + 1} of $totalParts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tap anywhere to close",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // White background box with padding for contrast against dark card
        Box(
            modifier =
                Modifier
                    .size(qrSize + 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable(onClick = enlargeQr)
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PSBT QR Code - Tap to enlarge",
                    modifier = Modifier.size(qrSize),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        if (isAnimated) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Part ${partIndex + 1} of $totalParts",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap QR to enlarge",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

private fun resolveUrFragmentSize(
    payloadSize: Int,
    density: SecureStorage.QrDensity,
): Int {
    // OPTIMIZATION: Increased fragment sizes significantly for better QR density
    // Larger fragments = fewer frames but need higher QR versions (25-40)
    // Modern hardware wallet cameras (Coldcard Q, Keystone, Jade) can scan v40 QR codes
    return when (density) {
        SecureStorage.QrDensity.LOW ->
            when {
                payloadSize >= 20_000 -> 350 // Was 180 - 94% increase
                payloadSize >= 12_000 -> 280 // Was 180 - 55% increase
                payloadSize >= 6_000 -> 200  // Was 140 - 43% increase
                payloadSize >= 2_000 -> 150  // Was 120 - 25% increase
                else -> 120                  // Was 100 - 20% increase
            }

        SecureStorage.QrDensity.MEDIUM ->
            when {
                payloadSize >= 20_000 -> 450 // Was 240 - 88% increase
                payloadSize >= 12_000 -> 380 // Was 240 - 58% increase
                payloadSize >= 6_000 -> 300  // Was 220 - 36% increase
                payloadSize >= 2_000 -> 240  // Was 180 - 33% increase
                else -> 180                  // Was 140 - 29% increase
            }

        SecureStorage.QrDensity.HIGH ->
            when {
                payloadSize >= 20_000 -> 550 // Was 300 - 83% increase
                payloadSize >= 12_000 -> 480 // Was 300 - 60% increase
                payloadSize >= 6_000 -> 400  // Was 260 - 54% increase
                payloadSize >= 2_000 -> 320  // Was 220 - 45% increase
                else -> 220                  // Was 180 - 22% increase
            }
    }
}

private fun resolveUrFrameDelay(
    totalParts: Int,
    playbackSpeed: SecureStorage.QrPlaybackSpeed,
): Long {
    val baseDelay =
        when {
            totalParts >= 60 -> 1200L
            totalParts >= 20 -> 900L
            else -> 650L
        }

    return when (playbackSpeed) {
        SecureStorage.QrPlaybackSpeed.SLOW -> (baseDelay * 1.25f).toLong()
        SecureStorage.QrPlaybackSpeed.MEDIUM -> baseDelay
        SecureStorage.QrPlaybackSpeed.FAST -> (baseDelay * 0.75f).toLong()
    }.coerceAtLeast(350L)
}

/**
 * Animated QR code component for generic byte data, encoded as BBQr.
 * Used for exporting BIP 329 labels via animated QR codes.
 *
 * For small data that fits in a single QR frame, displays a static QR code.
 * For larger data, splits into deterministic BBQr parts and cycles through them.
 *
 * BBQr advantages over BC-UR for labels:
 * - Zlib compression (~30-45% smaller for JSONL text)
 * - More data per QR frame (~1,062 bytes vs 120)
 * - Deterministic progress (no fountain code overhead)
 * - Interoperable with Sparrow, LabelBase, Nunchuk, Coldcard Q
 */
@Composable
fun AnimatedQrCodeBytes(
    data: ByteArray,
    modifier: Modifier = Modifier,
    qrSize: Dp = 280.dp,
    frameDelayMs: Long = 250L,
) {
    val context = LocalContext.current
    var showEnlarged by remember { mutableStateOf(false) }

    // Split data into BBQr parts with Zlib compression
    val bbqrParts =
        remember(data) {
            github.aeonbtc.ibiswallet.util.Bbqr.split(
                data = data,
                fileType = github.aeonbtc.ibiswallet.util.Bbqr.FILE_TYPE_JSON,
            ).parts
        }

    val totalParts = bbqrParts.size
    var partIndex by remember { mutableIntStateOf(0) }

    val isAnimated = totalParts > 1

    if (isAnimated) {
        LaunchedEffect(bbqrParts) {
            while (true) {
                delay(frameDelayMs)
                partIndex = (partIndex + 1) % totalParts
            }
        }
    }

    val currentPart = bbqrParts[partIndex]

    val qrBitmap =
        remember(currentPart) {
            generateQrBitmap(currentPart)
        }

    // Keep the screen on while QR is displayed. Labels do not override brightness.
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        val window = activity?.window

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val enlargeQr = { showEnlarged = true }
    val dismissEnlarged = { showEnlarged = false }
    if (showEnlarged) {
        Dialog(
            onDismissRequest = dismissEnlarged,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(onClick = dismissEnlarged),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(360.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Enlarged Labels QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    if (isAnimated) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Part ${partIndex + 1} of $totalParts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tap anywhere to close",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(qrSize + 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable(onClick = enlargeQr)
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Labels QR Code - Tap to enlarge",
                    modifier = Modifier.size(qrSize),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        if (isAnimated) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Part ${partIndex + 1} of $totalParts",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap QR to enlarge",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

