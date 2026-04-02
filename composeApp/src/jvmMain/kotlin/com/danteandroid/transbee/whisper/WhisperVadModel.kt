package com.danteandroid.transbee.whisper

import com.danteandroid.transbee.utils.HttpDownloader
import java.io.File
import java.time.Duration

object WhisperVadModel {

    const val FILE_NAME = "ggml-silero-v6.2.0.bin"
    private val downloadUrl = "https://huggingface.co/ggml-org/whisper-vad/resolve/main/$FILE_NAME"

    fun modelFile(): File = File(WhisperModelPaths.modelsDirectory(), FILE_NAME)

    suspend fun ensureDownloaded(
        onProgress: (received: Long, total: Long?) -> Unit = { _, _ -> },
    ): File {
        return HttpDownloader.downloadFile(
            url = downloadUrl,
            dest = modelFile(),
            userAgent = "Whisperit/1.0 (compatible; Whisper-VAD-Downloader)",
            timeout = Duration.ofMinutes(5),
            onProgress = onProgress
        )
    }
}
