package com.danteandroid.kaptionit.process

import com.danteandroid.kaptionit.native.BundledNativeTools
import com.danteandroid.kaptionit.settings.ToolingSettings
import com.danteandroid.kaptionit.settings.TranscriptionCacheKeyDto
import com.danteandroid.kaptionit.settings.TranscriptionCacheStore
import com.danteandroid.kaptionit.srt.SubtitleBuilder
import com.danteandroid.kaptionit.ui.TranslationTaskStats
import com.danteandroid.kaptionit.utils.JvmResourceStrings
import com.danteandroid.kaptionit.utils.subtitleOutputFile
import com.danteandroid.kaptionit.utils.toReadableByteSize
import com.danteandroid.kaptionit.whisper.WhisperCliArgs
import com.danteandroid.kaptionit.whisper.WhisperJsonParser
import com.danteandroid.kaptionit.whisper.WhisperVadModel
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.err_audio_extract
import kaptionit.composeapp.generated.resources.err_vad_download
import kaptionit.composeapp.generated.resources.err_whisper
import kaptionit.composeapp.generated.resources.err_whisper_dll_missing
import kaptionit.composeapp.generated.resources.msg_extract_audio
import kaptionit.composeapp.generated.resources.msg_processing_line
import kaptionit.composeapp.generated.resources.msg_transcribing
import kaptionit.composeapp.generated.resources.msg_transcription_cache_hit
import kaptionit.composeapp.generated.resources.msg_vad_downloading
import kaptionit.composeapp.generated.resources.msg_whisper_line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    val tmpDir = Files.createTempDirectory("kaptionit_")
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
