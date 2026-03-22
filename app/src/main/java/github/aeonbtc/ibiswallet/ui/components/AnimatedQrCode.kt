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
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import kotlinx.coroutines.delay

/**
 * Animated QR code component that displays BC-UR encoded data as cycling QR frames.
 * Used for exporting PSBTs to hardware wallets via animated QR codes.
 *
 * For small PSBTs that fit in a single QR frame, displays a static QR code.
 * For larger PSBTs, creates fountain-coded UR parts and cycles through them.
 *
 * Optimized for hardware wallet cameras (SeedSigner, Coldcard, Keystone):
 * - 250ms frame delay (~4 FPS) for slow cameras
 * - 120-byte fragments for lower QR density
 * - Explicit error correction level L (fountain codes handle redundancy)
 * - Thick white border for contrast
 * - Screen brightness boost and keep-screen-on
 * - Tap to enlarge for full-screen display
 */
@Composable
fun AnimatedQrCode(
    psbtBase64: String,
    modifier: Modifier = Modifier,
    qrSize: Dp = 280.dp,
    frameDelayMs: Long = 250L, // ~4 FPS, optimized for hardware wallet cameras
) {
    val context = LocalContext.current
    var showEnlarged by remember { mutableStateOf(false) }

    val psbtBytes =
        remember(psbtBase64) {
            android.util.Base64.decode(psbtBase64, android.util.Base64.DEFAULT)
        }

    // Create the UR encoder with fountain codes
    // 120-byte max fragment keeps QR density low for slow cameras
    val encoder =
        remember(psbtBytes) {
            val cryptoPsbt = CryptoPSBT(psbtBytes)
            val ur = cryptoPsbt.toUR()
            UREncoder(ur, 120, 10, 0)
        }

    val totalParts =
        remember(encoder) {
            encoder.seqLen
        }

    var currentPart by remember { mutableStateOf(encoder.nextPart()) }
    var partIndex by remember { mutableIntStateOf(1) }

    // Only animate if there are multiple parts
    val isAnimated = totalParts > 1

    if (isAnimated) {
        LaunchedEffect(encoder) {
            while (true) {
                delay(frameDelayMs)
                currentPart = encoder.nextPart()
                partIndex = ((partIndex) % totalParts) + 1
            }
        }
    }

    val qrBitmap =
        remember(currentPart) {
            generateQrBitmap(currentPart.uppercase())
        }

    // Boost screen brightness and keep screen on while QR is displayed
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        window?.let {
            val params = it.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
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
                            text = "Part $partIndex of $totalParts",
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
                text = "Part $partIndex of $totalParts",
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

    // Boost screen brightness while QR is displayed
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        window?.let {
            val params = it.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
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

