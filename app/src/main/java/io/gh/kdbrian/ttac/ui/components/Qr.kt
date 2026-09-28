package io.gh.kdbrian.ttac.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type
import java.util.concurrent.Executors

/**
 * QR code rendered on Canvas as rounded dots, with the three finder eyes drawn as soft squares.
 * No backing card: on dark themes the dots are light (an inverted QR, which scanners read).
 */
@Composable
fun QrCode(content: String, color: Color, modifier: Modifier = Modifier) {
    val dark = LocalPalette.current.isDark
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        }.getOrNull()
    }
    Canvas(modifier) {
        val m = matrix ?: return@Canvas
        val pad = 0f
        val n = m.width
        val cell = (size.minDimension - pad * 2) / n
        fun inFinder(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
        val shade = if (dark) Color.White else Color(0xFF1A0B3D)
        val ink = Brush.linearGradient(
            listOf(androidx.compose.ui.graphics.lerp(color, shade, 0.35f), androidx.compose.ui.graphics.lerp(color, shade, 0.7f)),
            Offset.Zero, Offset(size.width, size.height),
        )
        for (y in 0 until n) for (x in 0 until n) {
            if (!m[x, y] || inFinder(x, y)) continue
            drawCircle(ink, cell * 0.46f, Offset(pad + (x + 0.5f) * cell, pad + (y + 0.5f) * cell))
        }
        for ((fx, fy) in listOf(0 to 0, n - 7 to 0, 0 to n - 7)) {
            val tl = Offset(pad + fx * cell, pad + fy * cell)
            drawRoundRect(ink, tl + Offset(cell * 0.5f, cell * 0.5f), Size(cell * 6f, cell * 6f), CornerRadius(cell * 1.8f), style = Stroke(cell))
            drawRoundRect(ink, tl + Offset(cell * 2f, cell * 2f), Size(cell * 3f, cell * 3f), CornerRadius(cell * 1f))
        }
    }
}

/**
 * Full-screen QR scanner. Asks for the camera only when opened; hands the first decoded text
 * that [accept] likes to [onResult].
 */
@Composable
fun QrScanner(accept: (String) -> Boolean, onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black) }
        if (granted) CameraFeed(accept, onResult)
        ScanFrame()
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                IconBubble(Glyph.CLOSE, onClose, Modifier.align(Alignment.CenterStart), tint = Color.White, label = "Close scanner")
                Txt("Scan to join", Type.title, Color.White, Modifier.align(Alignment.Center))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Txt(
                when {
                    denied -> "Camera access is needed to scan. You can still join with the session code."
                    else -> "Point at the QR code on the host's screen"
                },
                Type.body, Color.White.copy(alpha = 0.85f), Modifier.padding(bottom = 32.dp), TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CameraFeed(accept: (String) -> Boolean, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val acceptNow by rememberUpdatedState(accept)
    val resultNow by rememberUpdatedState(onResult)
    val executor = remember { Executors.newSingleThreadExecutor() }
    var done by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        // ALSO_INVERTED: hosts on a dark theme show light-on-dark codes.
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.ALSO_INVERTED to true))
        }
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { image ->
                image.use {
                    if (done) return@use
                    val plane = it.planes[0]
                    val buffer = plane.buffer
                    val bytes = ByteArray(buffer.remaining()).also { b -> buffer.get(b) }
                    val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
                    val text = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
                    reader.reset()
                    if (text != null && acceptNow(text)) {
                        done = true
                        ContextCompat.getMainExecutor(context).execute { resultNow(text) }
                    }
                }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

/** Bright corner brackets and a sweeping scan line over the live camera — no dimming. */
@Composable
private fun ScanFrame() {
    val t by rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "t")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val palette = LocalPalette.current
    Canvas(Modifier.fillMaxSize()) {
        val side = size.minDimension * 0.68f
        val tl = Offset((size.width - side) / 2, (size.height - side) / 2.3f)
        val len = side * 0.18f
        val w = 5.dp.toPx() + 2.dp.toPx() * pulse
        val c = palette.accent
        for ((ox, oy) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
            val corner = Offset(tl.x + ox * side, tl.y + oy * side)
            val dx = if (ox == 0f) 1f else -1f
            val dy = if (oy == 0f) 1f else -1f
            drawLine(c, corner, corner + Offset(len * dx, 0f), w, StrokeCap.Round)
            drawLine(c, corner, corner + Offset(0f, len * dy), w, StrokeCap.Round)
        }
        val y = tl.y + side * (0.08f + 0.84f * t)
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, c, Color.Transparent), tl.x, tl.x + side), Offset(tl.x + 12f, y), Offset(tl.x + side - 12f, y), 3.dp.toPx())
    }
}
