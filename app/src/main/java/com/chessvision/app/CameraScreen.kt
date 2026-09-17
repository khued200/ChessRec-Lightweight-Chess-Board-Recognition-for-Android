package com.chessvision.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.viewinterop.AndroidView
import com.chessvision.app.ui.theme.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onCaptured: (Bitmap?) -> Unit,
    onNoCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!cameraFailed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        try {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val capture = ImageCapture.Builder().build()
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                            imageCapture = capture
                        } catch (e: Exception) {
                            cameraFailed = true
                        }
                    }, ContextCompatExecutor(ctx))
                    previewView
                }
            )
        } else {
            LaunchedEffect(Unit) { onNoCamera() }
        }

        // top bar
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconCircle("‹", onBack)
            Text(
                "align the board",
                color = Ivory,
                fontFamily = MonoFont,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .border(1.dp, PanelLine, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
            Spacer(Modifier.size(36.dp))
        }

        // board guide overlay
        BoardGuideOverlay(Modifier.align(Alignment.Center).fillMaxSize(0.78f).aspectRatio(1f))

        // shutter
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
                .size(70.dp)
                .clip(CircleShape)
                .background(Ivory)
                .border(4.dp, Ivory.copy(alpha = 0.35f), CircleShape)
                .clickable(enabled = imageCapture != null) {
                    val capture = imageCapture ?: return@clickable
                    capture.takePicture(
                        ContextCompatExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                val bitmap = imageProxyToBitmap(image)
                                image.close()
                                onCaptured(bitmap)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onCaptured(null)
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun IconCircle(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, PanelLine, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = Ivory, fontSize = 18.sp)
    }
}

/** Faint 8x8 grid with camera-style corner brackets — the same guide as the web version. */
@Composable
private fun BoardGuideOverlay(modifier: Modifier) {
    Canvas(modifier) {
        val step = size.width / 8f
        for (i in 1 until 8) {
            drawLine(Ivory.copy(alpha = 0.35f), Offset(i * step, 0f), Offset(i * step, size.height), strokeWidth = 1f)
            drawLine(Ivory.copy(alpha = 0.35f), Offset(0f, i * step), Offset(size.width, i * step), strokeWidth = 1f)
        }
        val bracket = size.width * 0.12f
        val stroke = Stroke(width = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // four corner brackets
        drawPath(cornerPath(0f, 0f, bracket, bracket, right = false, bottom = false), Brass, style = stroke)
        drawPath(cornerPath(size.width, 0f, bracket, bracket, right = true, bottom = false), Brass, style = stroke)
        drawPath(cornerPath(0f, size.height, bracket, bracket, right = false, bottom = true), Brass, style = stroke)
        drawPath(cornerPath(size.width, size.height, bracket, bracket, right = true, bottom = true), Brass, style = stroke)
    }
}

private fun cornerPath(x: Float, y: Float, dx: Float, dy: Float, right: Boolean, bottom: Boolean) =
    androidx.compose.ui.graphics.Path().apply {
        val hx = if (right) -dx else dx
        val vy = if (bottom) -dy else dy
        moveTo(x, y + vy)
        lineTo(x, y)
        lineTo(x + hx, y)
    }

private fun imageProxyToBitmap(image: androidx.camera.core.ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotation = image.imageInfo.rotationDegrees
    val rotated = if (rotation == 0) {
        bitmap
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    return cropToGuideSquare(rotated)
}

/**
 * Crops a centered square matching what [BoardGuideOverlay] visually shows
 * the user (78% of the shorter side, centered) — otherwise the guide is
 * purely decorative and the model receives whatever background surrounds
 * the board too, once the full frame gets squashed to 644x644.
 *
 * Caveat: this assumes the on-screen preview and the captured image share
 * roughly the same aspect ratio and framing, which holds for CameraX's
 * default back-camera config on most phones but isn't guaranteed on every
 * device. If you see the crop feel slightly off on a given phone, that
 * preview/capture aspect-ratio mismatch is why.
 */
private fun cropToGuideSquare(bitmap: Bitmap, guideFraction: Float = 0.78f): Bitmap {
    val shortSide = minOf(bitmap.width, bitmap.height)
    val cropSize = (shortSide * guideFraction).toInt().coerceAtLeast(1)
    val left = (bitmap.width - cropSize) / 2
    val top = (bitmap.height - cropSize) / 2
    return Bitmap.createBitmap(bitmap, left, top, cropSize, cropSize)
}

private fun ContextCompatExecutor(context: android.content.Context): Executor =
    androidx.core.content.ContextCompat.getMainExecutor(context)
