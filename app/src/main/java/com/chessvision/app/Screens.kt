package com.chessvision.app

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessvision.app.ui.theme.*

/** Which screen is currently on top of the single-activity state machine. */
sealed class Screen {
    data object Home : Screen()
    data object Camera : Screen()
    data class Recognizing(val thumbnail: Bitmap?) : Screen()
    data class Result(val fen: String) : Screen()
    data object Error : Screen()
}

@Composable
fun HomeScreen(onScan: () -> Unit, onUpload: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        ChessMark()
        Spacer(Modifier.height(22.dp))
        Text(
            "CHESSREC",
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Scan a position,\nopen it in Lichess.",
            color = Ivory,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 34.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Point your camera at a board. We read the pieces and hand you a ready-to-edit position — no account, no setup.",
            color = TextMuted,
            fontFamily = DisplayFont,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Scan position", onClick = onScan)
        Spacer(Modifier.height(10.dp))
        GhostButton("Upload a photo", onClick = onUpload)
        Spacer(Modifier.height(14.dp))
        Text(
            "runs on your device — nothing is uploaded",
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 11.5.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ChessMark() {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(Walnut, WalnutDark)))
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(Ivory))
                Box(Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight().background(Ivory))
            }
        }
    }
}

@Composable
fun RecognizingScreen(thumbnail: Bitmap?, recognizer: ChessQueriesRecognizer, onDone: (String?) -> Unit) {
    LaunchedEffect(thumbnail) {
        // The scanning animation below loops for as long as real inference
        // takes (typically a few hundred ms on a modern phone for this
        // quantized ViT-S model) instead of a fixed fake delay.
        val fen = thumbnail?.let { recognizer.recognize(it) }
        onDone(fen)
    }
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.55f
                )
            } ?: Box(Modifier.fillMaxSize().background(FeltDeep))

            // 8x8 grid where a diagonal "reading" band sweeps across, tying
            // the loading state to the subject (scanning squares) instead of
            // a generic spinner.
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until 8) {
                    Row(Modifier.weight(1f)) {
                        for (c in 0 until 8) {
                            val cellPos = (r + c) / 14f
                            val dist = kotlin.math.abs(cellPos - sweep)
                            val alpha = (0.9f - dist * 4f).coerceIn(0f, 0.9f)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, Brass.copy(alpha = 0.15f))
                                    .background(Brass.copy(alpha = alpha))
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("Reading the board…", color = TextMuted, fontFamily = MonoFont, fontSize = 13.sp)
    }
}

@Composable
fun ResultScreen(fen: String, onOpenLichess: () -> Unit, onScanAgain: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val board = remember(fen) { parseBoard(fen) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Text("POSITION FOUND", color = TextMuted, fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
        ) {
            board.forEach { row ->
                Row(Modifier.weight(1f)) {
                    row.forEach { sq ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (sq.isLight) BoardLight else BoardDark),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sq.glyph.isNotEmpty()) {
                                PieceGlyph(
                                    glyph = sq.glyph,
                                    isWhite = sq.isWhite,
                                    modifier = Modifier.fillMaxSize(0.78f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(FeltDeep, RoundedCornerShape(10.dp))
                .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                fen,
                color = Ivory,
                fontFamily = MonoFont,
                fontSize = 11.5.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (copied) "copied" else "copy",
                color = if (copied) BrassHi else TextMuted,
                fontFamily = MonoFont,
                fontSize = 11.sp,
                modifier = Modifier
                    .border(1.dp, if (copied) Brass else PanelLine, RoundedCornerShape(6.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(fen))
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("Open in Lichess Editor", onClick = onOpenLichess)
        Spacer(Modifier.height(10.dp))
        Text(
            "Scan another position",
            color = TextMuted,
            fontFamily = DisplayFont,
            fontSize = 13.sp,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onScanAgain)
                .padding(6.dp)
        )
    }
}

@Composable
private fun PieceGlyph(glyph: String, isWhite: Boolean, modifier: Modifier = Modifier) {
    // Piece color depends on the PIECE's color, not the square's — the
    // previous version tied glyph color to square color, which made white
    // pieces nearly invisible on light squares. Fill + stroke here mimics
    // how real piece sets (including Lichess') stay readable on any square.
    val fillColor = if (isWhite) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val strokeColor = if (isWhite) android.graphics.Color.BLACK else android.graphics.Color.WHITE

    androidx.compose.foundation.Canvas(modifier) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = size.minDimension * 0.82f
        }
        val cx = size.width / 2f
        val cy = size.height / 2f - (paint.ascent() + paint.descent()) / 2f
        drawContext.canvas.nativeCanvas.apply {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = size.minDimension * 0.07f
            paint.color = strokeColor
            drawText(glyph, cx, cy, paint)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = fillColor
            drawText(glyph, cx, cy, paint)
        }
    }
}

@Composable
fun ErrorScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(1.5.dp, Danger, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("!", color = Danger, fontSize = 24.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Couldn't read the board",
            color = Ivory,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Try moving closer, flattening the angle, or improving the light.",
            color = TextMuted,
            fontFamily = DisplayFont,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(26.dp))
        PrimaryButton("Try again", onClick = onRetry, fullWidth = false)
    }
}

// ---- shared button styles ----

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, fullWidth: Boolean = true) {
    Box(
        (if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(BrassHi, Brass)))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF241A05), fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, PanelLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Ivory, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
