package com.chessvision.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.exp

private const val MODEL_ASSET_PATH = "models/chessquerieslite-vits-644-int8.onnx"
// To try the fp16 variant instead (see convert_to_fp16.py — there's no
// ready-made fp16 ONNX for this "lite" model on HuggingFace, only fp32/int8),
// convert it yourself, drop it in assets/models/, and point this constant at
// it. Preprocessing/postprocessing below are unaffected either way — the
// fp16 conversion keeps float32 input/output tensors (keep_io_types=True),
// only the internal weights change precision.
private const val INPUT_SIZE = 644
private const val NUM_SQUARES = 64

// All three constants below are confirmed from the model's own training
// source (minimal_reproduction data-loading module), not guessed:
//   - eval_transform: pixel/255, plain resize to (res,res), then ImageNet
//     mean/std normalization, CHW layout.
//   - square_index(name) = (8 - rank) * 8 + file  ->  standard FEN order
//     (a8..h8, a7..h7, ..., a1..h1) — matches parseBoard() in Fen.kt as-is.
//   - PIECE_SYMBOLS = ".PNBRQKpnbrqk", index == class id.
private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
private const val PIECE_SYMBOLS = ".PNBRQKpnbrqk"
private const val CONFIDENCE_THRESHOLD = 0.5f

/**
 * Wraps the ChessQueries ONNX model. Model weights are licensed
 * PolyForm Noncommercial 1.0.0 — personal/noncommercial use only.
 * See https://github.com/JSeytre/chessqueries
 */
class ChessQueriesRecognizer(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    // Loaded lazily on first use, off the main thread (recognize() runs on
    // Dispatchers.Default), so app startup isn't blocked by the 36MB asset.
    private val session: OrtSession by lazy {
        val bytes = context.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
        env.createSession(bytes)
    }

    private val inputName: String by lazy { session.inputNames.iterator().next() }

    /** Runs entirely off the main thread. Returns a FEN, or null on low confidence. */
    suspend fun recognize(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        val inputData = preprocess(bitmap)
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

        OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                @Suppress("UNCHECKED_CAST")
                val batched = results[0].value as Array<Array<FloatArray>> // [1][64][13]
                postprocess(batched[0])
            }
        }
    }

    /**
     * Matches the model's own eval_transform: plain (non-aspect-preserving)
     * resize to 644x644, scale to [0,1], then ImageNet normalize, CHW layout.
     */
    private fun preprocess(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val channelSize = INPUT_SIZE * INPUT_SIZE
        val out = FloatArray(3 * channelSize)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            out[i] = (r - IMAGENET_MEAN[0]) / IMAGENET_STD[0]
            out[channelSize + i] = (g - IMAGENET_MEAN[1]) / IMAGENET_STD[1]
            out[2 * channelSize + i] = (b - IMAGENET_MEAN[2]) / IMAGENET_STD[2]
        }
        return out
    }

    /** logits: 64 squares already in FEN order (a8..h8, ..., a1..h1), 13 classes each. */
    private fun postprocess(logits: Array<FloatArray>): String? {
        val placement = StringBuilder()
        var confidenceSum = 0f

        for (rankRow in 0 until 8) {
            var emptyRun = 0
            for (file in 0 until 8) {
                val squareLogits = logits[rankRow * 8 + file]
                val (classIdx, confidence) = argmaxWithConfidence(squareLogits)
                confidenceSum += confidence
                val piece = PIECE_SYMBOLS[classIdx]
                if (piece == '.') {
                    emptyRun++
                } else {
                    if (emptyRun > 0) {
                        placement.append(emptyRun)
                        emptyRun = 0
                    }
                    placement.append(piece)
                }
            }
            if (emptyRun > 0) placement.append(emptyRun)
            if (rankRow != 7) placement.append('/')
        }

        val meanConfidence = confidenceSum / NUM_SQUARES
        if (meanConfidence < CONFIDENCE_THRESHOLD) return null

        // The model only predicts piece placement, not side-to-move/castling/
        // en-passant — same simplification the web MVP used. Lichess Editor
        // lets the user fix the rest.
        return "$placement w KQkq - 0 1"
    }

    private fun argmaxWithConfidence(logits: FloatArray): Pair<Int, Float> {
        var maxIdx = 0
        var maxVal = logits[0]
        for (i in 1 until logits.size) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }
        var sumExp = 0f
        for (v in logits) sumExp += exp(v - maxVal)
        val confidence = 1f / sumExp // softmax probability of the argmax class
        return maxIdx to confidence
    }
}
