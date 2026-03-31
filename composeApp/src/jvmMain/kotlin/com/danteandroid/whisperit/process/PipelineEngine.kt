package com.danteandroid.whisperit.process

import com.danteandroid.whisperit.native.BundledNativeTools
import com.danteandroid.whisperit.settings.ToolingSettings
import com.danteandroid.whisperit.settings.TranscriptionCacheKeyDto
import com.danteandroid.whisperit.settings.TranscriptionCacheStore
import com.danteandroid.whisperit.srt.SubtitleBuilder
import com.danteandroid.whisperit.ui.TranslationTaskStats
import com.danteandroid.whisperit.utils.JvmResourceStrings
import com.danteandroid.whisperit.utils.subtitleOutputFile
import com.danteandroid.whisperit.utils.toReadableByteSize
import com.danteandroid.whisperit.whisper.WhisperCliArgs
import com.danteandroid.whisperit.whisper.WhisperJsonParser
import com.danteandroid.whisperit.whisper.WhisperVadModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import whisperit.composeapp.generated.resources.*
import java.io.File
import java.nio.file.Files

interface PipelineListener {
    fun onStateChange(phase: PipelinePhase, message: String, progress: Float)
    fun onProgress(message: String, progress: Float? = null)
    fun onCompleted(outputPath: String?, translationStats: TranslationTaskStats?)
    fun onError(error: String)
}

object PipelineEngine {

    suspend fun execute(
        id: String,
        file: File,
        cfg: ToolingSettings,
        listener: PipelineListener
    ) {
        var workDir: java.nio.file.Path? = null
        var recognitionDurationMs = 0L
        try {
            val cacheKey = transcriptionCacheKey(file, cfg)
            val cachedDoc = withContext(Dispatchers.IO) { TranscriptionCacheStore.get(cacheKey) }
            val whisperDoc = cachedDoc?.let { hit ->
                listener.onStateChange(
                    PipelinePhase.Translating,
                    JvmResourceStrings.text(Res.string.msg_transcription_cache_hit),
                    0.55f
                )
                hit
            } ?: run {
                val recStartMs = System.currentTimeMillis()
                listener.onStateChange(
                    PipelinePhase.Extracting,
                    JvmResourceStrings.text(Res.string.msg_extract_audio),
                    0.05f
                )

                val doc = run {
                    val ffmpegResolved = BundledNativeTools.resolveFfmpegPath()
                    val whisperResolved = BundledNativeTools.resolveWhisperBinaryPath()
                    val tmpDir = Files.createTempDirectory("whisperit_")
                    workDir = tmpDir
                    val wavPath = tmpDir.resolve("audio.wav")
                    val extractCmd = listOf(
                        ffmpegResolved, "-y", "-i", file.absolutePath,
                        "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le",
                        wavPath.toString(),
                    )
                    val extractCode = ProcessRunner.run(
                        extractCmd,
                        onStdoutLine = { line ->
                            listener.onProgress(JvmResourceStrings.text(Res.string.msg_processing_line, line.take(120)))
                        },
                    )
                    if (extractCode != 0) {
                        error(JvmResourceStrings.text(Res.string.err_audio_extract, extractCode))
                    }
                    
                    listener.onStateChange(
                        PipelinePhase.Transcribing,
                        JvmResourceStrings.text(Res.string.msg_transcribing),
                        0.35f
                    )

                    if (cfg.whisperVadEnabled) {
                        listener.onProgress(JvmResourceStrings.text(Res.string.msg_vad_downloading))
                        try {
                            WhisperVadModel.ensureDownloaded(
                                onProgress = { received, total ->
                                    val progressStr = if (total != null && total > 0) {
                                        " [${received.toReadableByteSize()} / ${total.toReadableByteSize()}]"
                                    } else {
                                        " [${received.toReadableByteSize()}]"
                                    }
                                    listener.onProgress(JvmResourceStrings.text(Res.string.msg_vad_downloading) + progressStr)
                                }
                            )
                        } catch (e: Throwable) {
                            error(JvmResourceStrings.text(Res.string.err_vad_download, e.message ?: e.toString()))
                        }
                    }

                    val outBase = tmpDir.resolve("whisper_out").toAbsolutePath().toString()
                    listener.onProgress(JvmResourceStrings.text(Res.string.msg_transcribing))

                    val whisperCode = runWhisperCli(
                        whisperResolved, cfg,
                        wavPath.toString(), outBase,
                        useVad = cfg.whisperVadEnabled,
                        onMessage = { msg -> listener.onProgress(msg) }
                    )
                    if (whisperCode != 0) {
                        if (whisperCode == -1073741515) {
                            error(JvmResourceStrings.text(Res.string.err_whisper_dll_missing, whisperCode))
                        } else {
                            error(JvmResourceStrings.text(Res.string.err_whisper, whisperCode))
                        }
                    }
                    val jsonText = withContext(Dispatchers.IO) {
                        Files.readString(java.nio.file.Paths.get("$outBase.json"))
                    }
                    WhisperJsonParser.parseResult(jsonText)
                }
                recognitionDurationMs = System.currentTimeMillis() - recStartMs
                withContext(Dispatchers.IO) { TranscriptionCacheStore.put(cacheKey, doc) }
                doc
            }

            val buildResult = SubtitleBuilder.buildSubtitleExportFiles(
                cfg = cfg,
                whisperDoc = whisperDoc,
                recognitionDurationMs = recognitionDurationMs,
                onProgressUpdate = { progress, message ->
                    listener.onStateChange(PipelinePhase.Translating, message, progress)
                }
            )

            val outputPaths = withContext(Dispatchers.IO) {
                buildResult.files.map { payload ->
                    val outFile = file.subtitleOutputFile(cfg.exportFormat, payload.nameSuffix)
                    Files.writeString(outFile.toPath(), payload.body)
                    outFile.absolutePath
                }
            }

            listener.onCompleted(outputPaths.firstOrNull(), buildResult.translationStats)

        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            listener.onError(e.message ?: e.toString())
        } finally {
            workDir?.let { dir ->
                withContext(Dispatchers.IO) {
                    runCatching { dir.toFile().deleteRecursively() }
                }
            }
        }
    }

    private fun transcriptionCacheKey(file: File, cfg: ToolingSettings): TranscriptionCacheKeyDto {
        val path = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        return TranscriptionCacheKeyDto(
            fileKey = path,
            fileSize = file.length(),
            fileLastModified = file.lastModified(),
            whisperModel = cfg.whisperModel.trim(),
            whisperLanguage = cfg.whisperLanguage.trim().lowercase().ifEmpty { "auto" },
            whisperVadEnabled = cfg.whisperVadEnabled,
            whisperThreadCount = WhisperCliArgs.threadCount(),
        )
    }

    private suspend fun runWhisperCli(
        whisperResolved: String,
        cfg: ToolingSettings,
        wavPath: String,
        outBase: String,
        useVad: Boolean,
        onMessage: (String) -> Unit
    ): Int {
        val lang = cfg.whisperLanguage.trim().lowercase().ifEmpty { "auto" }
        val cmd = buildList {
            add(whisperResolved)
            add("-t")
            add(WhisperCliArgs.threadCount().toString())
            add("-m")
            add(cfg.whisperModel)
            add("-l")
            add(lang)
            add("-mc")
            add("0")
            add("-f")
            add(wavPath)
            add("-oj")
            add("-of")
            add(outBase)
            if (useVad) {
                add("--vad")
                add("-vm")
                add(WhisperVadModel.modelFile().absolutePath)
            }
        }
        return ProcessRunner.run(
            cmd,
            onStdoutLine = { line ->
                onMessage(JvmResourceStrings.text(Res.string.msg_whisper_line, line.take(120)))
            },
        )
    }
}
