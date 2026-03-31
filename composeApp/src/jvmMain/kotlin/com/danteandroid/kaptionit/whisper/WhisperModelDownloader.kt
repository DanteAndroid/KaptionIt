package com.danteandroid.kaptionit.whisper

import com.danteandroid.kaptionit.utils.HttpDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration

data class WhisperDownloadResult(
    val file: File,
    val skipped: Boolean,
)

object WhisperModelDownloader {

    suspend fun downloadIfNeeded(
        option: WhisperModelOption,
        force: Boolean,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): WhisperDownloadResult = withContext(Dispatchers.IO) {
        val dir = WhisperModelPaths.modelsDirectory()
        val dest = File(dir, option.fileName)
        
        if (!force && dest.isFile && dest.length() > 0L) {
            return@withContext WhisperDownloadResult(dest, true)
        }
        
        if (force) {
            dest.delete()
        }
        
        val downloadedFile = HttpDownloader.downloadFile(
            url = option.url,
            dest = dest,
            userAgent = "Whisperit/1.0 (compatible; Whisper-Model-Downloader)",
            timeout = Duration.ofHours(4),
            onProgress = onProgress
        )
        
        WhisperDownloadResult(downloadedFile, false)
    }
}
