package com.capsule.app.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * T075 (Phase 6 US4) — on-device OCR wrapper around ML Kit's Latin-script
 * text recogniser. **Graceful degrade**: any failure (missing URI,
 * decoder error, ML Kit init failure) returns an empty [Result] rather
 * than throwing, mirroring the pattern set by
 * [com.capsule.app.ai.NanoLlmProvider] / [NanoSummariser].
 *
 * Principle VIII (Collect Only What You Use): the raw OCR text is only
 * returned for in-memory URL extraction by the caller and is never
 * persisted. See [com.capsule.app.continuation.ScreenshotUrlExtractWorker].
 *
 * The bundled model variant is used (see `implementation(libs.mlkit.text.recognition)`
 * in `app/build.gradle.kts`) so recognition is fully on-device and does
 * not require Google Play Services to download a model.
 */
open class OcrEngine {

    data class Result(
        /** The raw recognised text, concatenated across blocks with `\n`. */
        val text: String,
        /** Whether ML Kit was able to run at all. `false` on any failure. */
        val ok: Boolean
    ) {
        companion object {
            val Empty: Result = Result(text = "", ok = false)
        }
    }

    /**
     * Run ML Kit text recognition against [imageUri]. Never throws —
     * on any failure (permissions, decoder, ML Kit init) returns
     * [Result.Empty].
     */
    open suspend fun extractText(context: Context, imageUri: Uri): Result = runCatching {
        val input = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
        if (visionText == null) {
            Result.Empty
        } else {
            Result(text = visionText.text, ok = true)
        }
    }.getOrElse { Result.Empty }
}
