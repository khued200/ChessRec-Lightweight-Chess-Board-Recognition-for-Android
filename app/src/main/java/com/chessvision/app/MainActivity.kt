package com.chessvision.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chessvision.app.ui.theme.ChessVisionTheme
import com.chessvision.app.ui.theme.Felt

class MainActivity : ComponentActivity() {

    private var pendingAction: (() -> Unit)? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingAction?.invoke()
        pendingAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChessVisionTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Felt) {
                    ChessVisionApp(
                        onRequestCameraPermission = { onGranted ->
                            val ctx = this
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                onGranted()
                            } else {
                                pendingAction = onGranted
                                requestCameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChessVisionApp(onRequestCameraPermission: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    // Created once and kept for the app's lifetime; the ONNX session itself
    // is loaded lazily on first recognize() call, off the main thread.
    val recognizer = remember { ChessQueriesRecognizer(context.applicationContext) }

    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = try {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        } catch (e: Exception) {
            null
        }
        screen = Screen.Recognizing(bitmap)
    }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            onScan = {
                onRequestCameraPermission { screen = Screen.Camera }
            },
            onUpload = { pickImage.launch("image/*") }
        )

        is Screen.Camera -> CameraScreen(
            onBack = { screen = Screen.Home },
            onCaptured = { bitmap -> screen = Screen.Recognizing(bitmap) },
            onNoCamera = { pickImage.launch("image/*") }
        )

        is Screen.Recognizing -> RecognizingScreen(
            thumbnail = s.thumbnail,
            recognizer = recognizer,
            onDone = { fen ->
                screen = if (fen != null) Screen.Result(fen) else Screen.Error
            }
        )

        is Screen.Result -> ResultScreen(
            fen = s.fen,
            onOpenLichess = {
                val url = lichessEditorUrl(s.fen)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
            onScanAgain = {
                onRequestCameraPermission { screen = Screen.Camera }
            }
        )

        is Screen.Error -> ErrorScreen(
            onRetry = {
                onRequestCameraPermission { screen = Screen.Camera }
            }
        )
    }
}
