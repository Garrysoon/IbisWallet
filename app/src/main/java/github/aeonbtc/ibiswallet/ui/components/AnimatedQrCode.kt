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
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import kotlinx.coroutines.delay

/**
 * Animated QR code component that displays BC-UR encoded data as cycling QR frames.
 * Used for exporting PSBTs/PSETs to hardware wallets via animated QR codes.
 *
 * Uses `ur:crypto-psbt` framing which carries both Bitcoin PSBT and Liquid PSET bytes
 * (Jade and other hardware wallets accept both through the same UR type).
 *
 * For small payloads that fit in a single QR frame, displays a static QR code.
 * For larger payloads, precomputes a stable UR cycle and loops through it.
 *
 * Optimized for hardware wallet cameras (SeedSigner, Coldcard, Keystone, Jade):
 * - Slower cadence for Jade and other lower-FPS cameras
 * - Moderate adaptive fragment sizing to balance frame count vs QR density
 * - Stable ordered part loop instead of an aggressive changing stream
 * - Optional pause/step controls for scanners that miss fast transitions
 * - Explicit error correction level L (fountain codes handle redundancy)
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

    val fragmentSize =
        remember(dataBytes, density) {
            resolveUrFragmentSize(
                payloadSize = dataBytes.size,
                density = density,
            )
        }

    // CryptoPSBT wraps arbitrary bytes as ur:crypto-psbt — works for both PSBT and PSET.
    // Use denser fragments for large payloads to avoid impractical 100+ part animations.
    val qrParts =
        remember(dataBytes, fragmentSize) {
            val cryptoPsbt = CryptoPSBT(dataBytes)
            val ur = cryptoPsbt.toUR()
            val encoder = UREncoder(ur, fragmentSize, 10, 0)
            List(encoder.seqLen) { encoder.nextPart() }
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
    return when (density) {
        SecureStorage.QrDensity.LOW ->
            when {
                payloadSize >= 12_000 -> 180
                payloadSize >= 6_000 -> 140
                payloadSize >= 2_000 -> 120
                else -> 100
            }

        SecureStorage.QrDensity.MEDIUM ->
            when {
                payloadSize >= 12_000 -> 240
                payloadSize >= 6_000 -> 220
                payloadSize >= 2_000 -> 180
                else -> 140
            }

        SecureStorage.QrDensity.HIGH ->
            when {
                payloadSize >= 12_000 -> 300
                payloadSize >= 6_000 -> 260
                payloadSize >= 2_000 -> 220
                else -> 180
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

