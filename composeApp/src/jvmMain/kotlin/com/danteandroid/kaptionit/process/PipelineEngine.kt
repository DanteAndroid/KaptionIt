package com.danteandroid.transbee.process

import com.danteandroid.transbee.native.BundledNativeTools
import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.settings.TranscriptionCacheKeyDto
import com.danteandroid.transbee.settings.TranscriptionCacheStore
import com.danteandroid.transbee.srt.SubtitleBuilder
import com.danteandroid.transbee.ui.TranslationTaskStats
import com.danteandroid.transbee.utils.HttpDownloader
import com.danteandroid.transbee.utils.JvmResourceStrings
import com.danteandroid.transbee.utils.extractMdFromZip
import com.danteandroid.transbee.utils.subtitleOutputFile
import com.danteandroid.transbee.utils.toReadableByteSize
import com.danteandroid.transbee.whisper.WhisperCliArgs
import com.danteandroid.transbee.whisper.WhisperJsonParser
import com.danteandroid.transbee.whisper.WhisperVadModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.dialog_mineru_help_url
import transbee.composeapp.generated.resources.err_audio_extract
import transbee.composeapp.generated.resources.err_mineru_api
import transbee.composeapp.generated.resources.err_mineru_token_expired
import transbee.composeapp.generated.resources.err_mineru_token_invalid
import transbee.composeapp.generated.resources.err_mineru_token_missing
import transbee.composeapp.generated.resources.err_mineru_zip
import transbee.composeapp.generated.resources.err_whisper
import transbee.composeapp.generated.resources.msg_extract_audio
import transbee.composeapp.generated.resources.msg_mineru_downloading
import transbee.composeapp.generated.resources.msg_mineru_extracting
import transbee.composeapp.generated.resources.msg_mineru_processing
import transbee.composeapp.generated.resources.msg_mineru_status_waiting
import transbee.composeapp.generated.resources.msg_mineru_uploading
import transbee.composeapp.generated.resources.msg_pdf_translating
import transbee.composeapp.generated.resources.msg_text_translate_progress
import transbee.composeapp.generated.resources.msg_text_translating
import transbee.composeapp.generated.resources.msg_transcribing
import transbee.composeapp.generated.resources.msg_transcription_cache_hit
import transbee.composeapp.generated.resources.msg_whisper_line
import transbee.composeapp.generated.resources.tasks_processing
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.time.Duration

interface PipelineListener {
    fun onStateChange(
        phase: PipelinePhase,
        message: String,
        progress: Float,
        indeterminate: Boolean = false
    )

    fun onProgress(message: String, progress: Float? = null, indeterminate: Boolean = false)
    fun onCompleted(outputPath: String?, translationStats: TranslationTaskStats?)
    fun onError(error: String)
}

object PipelineEngine {

    private const val MinerUDownloadTotalUnknown = "—"

    private val mineruDocExtensions = setOf(
        "pdf", "doc", "docx", "ppt", "pptx",
        "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp",
    )
    private val textExtensions = setOf("txt", "md")

    /** MinerU 文档或 txt/md 直译，非音视频字幕管道 */
    fun isDocumentSourceFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in mineruDocExtensions || ext in textExtensions
    }

    /** MinerU 轮询可能传入空串或 API 英文状态，统一为可读短文案，避免「（…）…」叠字 */
    private fun mineruProcessingStatusLabel(state: String): String {
        val t = state.trim()
        if (t.isEmpty() || t == "…") {
            return JvmResourceStrings.text(Res.string.tasks_processing)
        }
        if (t == "waiting") {
            return JvmResourceStrings.text(Res.string.msg_mineru_status_waiting)
        }
        return t
    }

    suspend fun execute(id: String, file: File, cfg: ToolingSettings, listener: PipelineListener) {
        val ext = file.extension.lowercase()
        when {
            ext in mineruDocExtensions -> {
                executeDocPipeline(file, cfg, listener)
                return
            }

            ext in textExtensions -> {
                executeTextTranslatePipeline(file, cfg, listener)
                return
            }
        }

        var workDir: java.nio.file.Path? = null
        try {
            val cacheKey = transcriptionCacheKey(file, cfg)
            val whisperDoc = TranscriptionCacheStore.get(cacheKey)?.also {
                listener.onStateChange(
                    PipelinePhase.Translating,
                    JvmResourceStrings.text(Res.string.msg_transcription_cache_hit),
                    0f,
                    false
                )
            } ?: run {
                listener.onStateChange(
                    PipelinePhase.Extracting,
                    JvmResourceStrings.text(Res.string.msg_extract_audio),
                    0f,
                    true
                )
                transcribeMedia(file, cfg, listener) {
                    workDir = it
                }.also { TranscriptionCacheStore.put(cacheKey, it) }
            }

            val buildResult = SubtitleBuilder.buildSubtitleExportFiles(
                cfg = cfg, whisperDoc = whisperDoc,
                recognitionDurationMs = 0L,
                onProgressUpdate = { progress, message ->
                    listener.onStateChange(
                        PipelinePhase.Translating,
                        message,
                        progress,
                        false
                    )
                }
            )

            val outputPath = buildResult.files.firstNotNullOfOrNull { payload ->
                val outFile = file.subtitleOutputFile(cfg.exportFormat, payload.nameSuffix)
                Files.writeString(outFile.toPath(), payload.body)
                outFile.absolutePath
            }
            listener.onCompleted(outputPath, buildResult.translationStats)
        } catch (e: Throwable) {
            if (e !is CancellationException) listener.onError(
                e.message ?: e.toString()
            ) else throw e
        } finally {
            workDir?.toFile()?.deleteRecursively()
        }
    }

    private suspend fun executeDocPipeline(
        file: File,
        cfg: ToolingSettings,
        listener: PipelineListener
    ) {
        val token = cfg.minerUToken.trim().takeIf { it.isNotEmpty() }
            ?: return listener.onError(JvmResourceStrings.text(Res.string.err_mineru_token_missing))

        val client = MinerUClient(token)
        var workDir: java.nio.file.Path? = null

        suspend fun <T> step(
            progress: Float,
            msgRes: org.jetbrains.compose.resources.StringResource,
            indeterminate: Boolean = false,
            block: suspend () -> T,
        ): T? {
            listener.onStateChange(
                PipelinePhase.Transcribing,
                JvmResourceStrings.text(msgRes),
                progress,
                indeterminate
            )
            return try {
                block()
            } catch (e: MinerUApiException) {
                handleMinerUError(e, listener); null
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                listener.onError(e.message ?: e.toString()); null
            }
        }

        suspend fun <T> step(
            progress: Float,
            message: String,
            indeterminate: Boolean = false,
            block: suspend () -> T,
        ): T? {
            listener.onStateChange(PipelinePhase.Transcribing, message, progress, indeterminate)
            return try {
                block()
            } catch (e: MinerUApiException) {
                handleMinerUError(e, listener); null
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                listener.onError(e.message ?: e.toString()); null
            }
        }

        val uploadTotal = file.length().coerceAtLeast(0L)
        val taskId = step(
            0f,
            JvmResourceStrings.text(
                Res.string.msg_mineru_uploading,
                0L.toReadableByteSize(),
                uploadTotal.toReadableByteSize(),
            ),
            false,
        ) {
            client.submit(file) { sent, total ->
                val totalSafe = total.coerceAtLeast(1L)
                val frac = (sent.toDouble() / totalSafe.toDouble()).toFloat().coerceIn(0f, 1f)
                listener.onProgress(
                    JvmResourceStrings.text(
                        Res.string.msg_mineru_uploading,
                        sent.toReadableByteSize(),
                        total.toReadableByteSize(),
                    ),
                    frac,
                    false,
                )
            }
        } ?: return
        val zipUrl = step(
            0f,
            JvmResourceStrings.text(
                Res.string.msg_mineru_processing,
                mineruProcessingStatusLabel("")
            ),
            true,
        ) {
            client.poll(taskId) { state ->
                listener.onProgress(
                    JvmResourceStrings.text(
                        Res.string.msg_mineru_processing,
                        mineruProcessingStatusLabel(state)
                    ),
                    null,
                    true,
                )
            }
        } ?: return

        val tmpDir =
            withContext(Dispatchers.IO) { Files.createTempDirectory("transbee_pdf_") }.also {
                workDir = it
            }
        val zipFile = tmpDir.resolve("result.zip").toFile()

        val downloadDone = step(
            0f,
            JvmResourceStrings.text(
                Res.string.msg_mineru_downloading,
                0L.toReadableByteSize(),
                MinerUDownloadTotalUnknown,
            ),
            false,
        ) {
            HttpDownloader.downloadFile(
                zipUrl,
                zipFile,
                timeout = Duration.ofMinutes(15),
                onProgress = { received, total ->
                    val totalStr = total?.toReadableByteSize() ?: MinerUDownloadTotalUnknown
                    val frac =
                        if (total != null && total > 0L) {
                            (received.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    listener.onProgress(
                        JvmResourceStrings.text(
                            Res.string.msg_mineru_downloading,
                            received.toReadableByteSize(),
                            totalStr,
                        ),
                        frac,
                        false,
                    )
                },
            )
            Unit
        }
        if (downloadDone == null) {
            workDir?.toFile()?.deleteRecursively()
            return
        }

        listener.onStateChange(
            PipelinePhase.Transcribing,
            JvmResourceStrings.text(Res.string.msg_mineru_extracting),
            0f,
            true
        )
        val destMdFile = File(file.parentFile, "${file.nameWithoutExtension}.md")
        val extractedMd = runCatching { extractMdFromZip(zipFile, destMdFile) }.getOrElse {
            listener.onError(JvmResourceStrings.text(Res.string.err_mineru_zip))
            workDir?.toFile()?.deleteRecursively()
            return
        }

        try {
            val mdContent = extractedMd.readText()
            val paragraphs = MdTranslator.splitParagraphs(mdContent)
            val translatableTotal = MdTranslator.countTranslatableParagraphs(paragraphs)
            listener.onStateChange(
                PipelinePhase.Translating,
                JvmResourceStrings.text(Res.string.msg_pdf_translating, 0, translatableTotal),
                if (translatableTotal == 0) 1f else 0f,
                false
            )
            val translated = MdTranslator.translateParagraphs(paragraphs, cfg) { done, total ->
                val t = total.coerceAtLeast(1)
                listener.onProgress(
                    JvmResourceStrings.text(Res.string.msg_pdf_translating, done, total),
                    done.toFloat() / t,
                    false,
                )
            }
            val assembled = MdTranslator.assemble(paragraphs, translated, cfg.pdfTranslateFormat)
            destMdFile.writeText(assembled)
            listener.onCompleted(destMdFile.absolutePath, null)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            workDir?.toFile()?.deleteRecursively()
            listener.onError(e.message ?: e.toString())
        }
    }

    /** txt/md 直接读取内容并翻译，不经 MinerU */
    private suspend fun executeTextTranslatePipeline(
        file: File,
        cfg: ToolingSettings,
        listener: PipelineListener
    ) {
        try {
            listener.onStateChange(
                PipelinePhase.Translating,
                JvmResourceStrings.text(Res.string.msg_text_translating),
                0f,
                false
            )
            val mdContent = withContext(Dispatchers.IO) { file.readText() }
            val paragraphs = MdTranslator.splitParagraphs(mdContent)
            val translated = MdTranslator.translateParagraphs(paragraphs, cfg) { done, total ->
                val t = total.coerceAtLeast(1)
                listener.onProgress(
                    JvmResourceStrings.text(Res.string.msg_text_translate_progress, done, total),
                    done.toFloat() / t,
                    false,
                )
            }
            val assembled = MdTranslator.assemble(paragraphs, translated, cfg.pdfTranslateFormat)
            val destFile = File(file.parentFile, "${file.nameWithoutExtension}_translated.md")
            withContext(Dispatchers.IO) { destFile.writeText(assembled) }
            listener.onCompleted(destFile.absolutePath, null)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            listener.onError(e.message ?: e.toString())
        }
    }

    private fun handleMinerUError(e: MinerUApiException, listener: PipelineListener) =
        when (e.code) {
            "A0202" -> listener.onError(JvmResourceStrings.text(Res.string.err_mineru_token_invalid))
            "A0211" -> {
                listener.onError(JvmResourceStrings.text(Res.string.err_mineru_token_expired))
                runCatching {
                    java.awt.Desktop.getDesktop()
                        .browse(URI(JvmResourceStrings.text(Res.string.dialog_mineru_help_url)))
                }
        }

            else -> listener.onError(
                JvmResourceStrings.text(
                    Res.string.err_mineru_api,
                    e.code,
                    e.message
                )
            )
    }

    private suspend fun transcribeMedia(
        file: File,
        cfg: ToolingSettings,
        listener: PipelineListener,
        onWorkDir: (java.nio.file.Path) -> Unit
    ): com.danteandroid.transbee.whisper.WhisperParseResult {
        val tmpDir = Files.createTempDirectory("transbee_").also(onWorkDir)
        val wavPath = tmpDir.resolve("audio.wav").toString()

        ProcessRunner.run(
            listOf(
                BundledNativeTools.resolveFfmpegPath(),
                "-y",
                "-i",
                file.absolutePath,
                "-ar",
                "16000",
                "-ac",
                "1",
                "-c:a",
                "pcm_s16le",
                wavPath
            )
        ).let { code ->
            if (code != 0) error(JvmResourceStrings.text(Res.string.err_audio_extract, code))
        }

        listener.onStateChange(
            PipelinePhase.Transcribing,
            JvmResourceStrings.text(Res.string.msg_transcribing),
            0f,
            true
        )
        if (cfg.whisperVadEnabled) WhisperVadModel.ensureDownloaded { _, _ -> }

        val outBase = tmpDir.resolve("whisper_out").toAbsolutePath().toString()
        runWhisperCli(
            BundledNativeTools.resolveWhisperBinaryPath(),
            cfg,
            wavPath,
            outBase,
            cfg.whisperVadEnabled
        ) { msg ->
            listener.onProgress(msg, null, true)
        }.let { code ->
            if (code != 0) error(JvmResourceStrings.text(Res.string.err_whisper, code))
        }

        return WhisperJsonParser.parseResult(Files.readString(java.nio.file.Paths.get("$outBase.json")))
    }

    private fun transcriptionCacheKey(file: File, cfg: ToolingSettings) = TranscriptionCacheKeyDto(
        fileKey = file.absolutePath,
        fileSize = file.length(),
        fileLastModified = file.lastModified(),
        whisperModel = cfg.whisperModel,
        whisperLanguage = cfg.whisperLanguage,
        whisperVadEnabled = cfg.whisperVadEnabled,
        whisperThreadCount = WhisperCliArgs.threadCount()
    )

    private suspend fun runWhisperCli(
        bin: String,
        cfg: ToolingSettings,
        wav: String,
        out: String,
        vad: Boolean,
        onMsg: (String) -> Unit
    ): Int =
        ProcessRunner.run(
            command = buildList {
                add(bin); add("-t"); add(WhisperCliArgs.threadCount().toString()); add("-m"); add(
                cfg.whisperModel
            )
                add("-l"); add(cfg.whisperLanguage.lowercase()); add("-mc"); add("0"); add("-f"); add(
                wav
            ); add("-oj"); add("-of"); add(out)
                if (vad) {
                    add("--vad"); add("-vm"); add(WhisperVadModel.modelFile().absolutePath)
                }
            },
            onStdoutLine = { line ->
                onMsg(
                    JvmResourceStrings.text(
                        Res.string.msg_whisper_line,
                        line.take(120)
                    )
                )
            }
        )
}
