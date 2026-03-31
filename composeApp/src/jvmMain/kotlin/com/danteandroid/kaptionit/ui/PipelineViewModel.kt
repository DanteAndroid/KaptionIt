package com.danteandroid.kaptionit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danteandroid.kaptionit.process.PipelineEngine
import com.danteandroid.kaptionit.process.PipelineListener
import com.danteandroid.kaptionit.process.PipelinePhase
import com.danteandroid.kaptionit.process.isCancellableByStopAll
import com.danteandroid.kaptionit.settings.ToolingSettings
import com.danteandroid.kaptionit.settings.ToolingSettingsStore
import com.danteandroid.kaptionit.settings.TranscriptionCacheStore
import com.danteandroid.kaptionit.translate.AppleTranslateBinary
import com.danteandroid.kaptionit.translate.TranslationEngine
import com.danteandroid.kaptionit.utils.JvmResourceStrings
import com.danteandroid.kaptionit.utils.OsUtils
import com.danteandroid.kaptionit.utils.toReadableByteSize
import com.danteandroid.kaptionit.whisper.WhisperModelDownloader
import com.danteandroid.kaptionit.whisper.WhisperModelOption
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.err_apple_translate_macos_only
import kaptionit.composeapp.generated.resources.err_apple_translate_missing
import kaptionit.composeapp.generated.resources.err_deepl_key
import kaptionit.composeapp.generated.resources.err_file_read
import kaptionit.composeapp.generated.resources.err_google_key
import kaptionit.composeapp.generated.resources.err_openai_key
import kaptionit.composeapp.generated.resources.err_queue_while_downloading
import kaptionit.composeapp.generated.resources.err_retry_failed
import kaptionit.composeapp.generated.resources.err_whisper_model_missing
import kaptionit.composeapp.generated.resources.msg_download_complete
import kaptionit.composeapp.generated.resources.msg_download_connecting
import kaptionit.composeapp.generated.resources.msg_download_reconnecting
import kaptionit.composeapp.generated.resources.msg_download_skipped_local
import kaptionit.composeapp.generated.resources.phase_cancelled
import kaptionit.composeapp.generated.resources.phase_done_short
import kaptionit.composeapp.generated.resources.phase_queued
import kaptionit.composeapp.generated.resources.warn_start_all_missing_files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class PipelineViewModel : ViewModel() {

    private val _tooling = MutableStateFlow(ToolingSettingsStore.loadOrDefault())
    val tooling: StateFlow<ToolingSettings> = _tooling.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskRecord>>(emptyList())
    val tasks: StateFlow<List<TaskRecord>> = _tasks.asStateFlow()

    private val _modelDownload = MutableStateFlow(ModelDownloadUiState())
    val modelDownload: StateFlow<ModelDownloadUiState> = _modelDownload.asStateFlow()

    private val fileQueue = ArrayDeque<Pair<String, File>>()
    private var pipelineJob: Job? = null
    private var currentRunningTaskId: String? = null
    private var modelDownloadJob: Job? = null

    fun clearTranscriptionCache() {
        TranscriptionCacheStore.clearAll()
    }

    fun updateTooling(transform: (ToolingSettings) -> ToolingSettings) {
        _tooling.update(transform)
        ToolingSettingsStore.save(_tooling.value)
    }

    fun downloadWhisperModel(option: WhisperModelOption, forceRedownload: Boolean) {
        if (_modelDownload.value.active) return
        _modelDownload.value = ModelDownloadUiState(
            active = true,
            fileName = option.fileName,
            message = if (forceRedownload) {
                JvmResourceStrings.text(Res.string.msg_download_reconnecting)
            } else {
                JvmResourceStrings.text(Res.string.msg_download_connecting)
            },
        )
        modelDownloadJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = WhisperModelDownloader.downloadIfNeeded(
                    option = option,
                    force = forceRedownload,
                    onProgress = { received, total ->
                        val p = if (total != null && total > 0) {
                            (received.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        _modelDownload.value = ModelDownloadUiState(
                            active = true,
                            fileName = option.fileName,
                            progress = p,
                            receivedBytes = received,
                            totalBytes = total,
                            message = received.toReadableByteSize() +
                                    if (total != null && total > 0) " / ${total.toReadableByteSize()}" else "",
                        )
                    },
                )
                updateTooling { it.copy(whisperModel = result.file.absolutePath) }
                _modelDownload.value = ModelDownloadUiState(
                    active = false,
                    fileName = option.fileName,
                    progress = 1f,
                    message = if (result.skipped) {
                        JvmResourceStrings.text(Res.string.msg_download_skipped_local)
                    } else {
                        JvmResourceStrings.text(Res.string.msg_download_complete)
                    },
                    skippedExisting = result.skipped,
                )
            } catch (e: CancellationException) {
                _modelDownload.value = ModelDownloadUiState()
                throw e
            } catch (e: Exception) {
                _modelDownload.value = ModelDownloadUiState(
                    active = false,
                    fileName = option.fileName,
                    error = e.message ?: e.toString(),
                )
            } finally {
                modelDownloadJob = null
            }
        }
    }

    fun cancelModelDownload() {
        if (!_modelDownload.value.active) return
        modelDownloadJob?.cancel()
    }

    fun enqueuePipeline(videoFile: File): String? {
        if (_modelDownload.value.active) {
            return JvmResourceStrings.text(Res.string.err_queue_while_downloading)
        }
        if (!videoFile.isFile) {
            return JvmResourceStrings.text(Res.string.err_file_read, videoFile.path)
        }
        val cfg = _tooling.value
        if (cfg.whisperModel.isBlank()) {
            return JvmResourceStrings.text(Res.string.err_whisper_model_missing)
        }
        when (cfg.translationEngine) {
            TranslationEngine.APPLE -> {
                if (!OsUtils.isMacOs()) {
                    return JvmResourceStrings.text(Res.string.err_apple_translate_macos_only)
                }
                if (AppleTranslateBinary.resolvePath(cfg.appleTranslateBinary) == null) {
                    return JvmResourceStrings.text(Res.string.err_apple_translate_missing)
                }
            }
            TranslationEngine.GOOGLE -> {
                if (cfg.googleApiKey.isBlank()) {
                    return JvmResourceStrings.text(Res.string.err_google_key)
                }
            }
            TranslationEngine.DEEPL -> {
                if (cfg.deeplApiKey.isBlank()) return JvmResourceStrings.text(Res.string.err_deepl_key)
            }
            TranslationEngine.OPENAI -> {
                if (cfg.openAiKey.isBlank()) return JvmResourceStrings.text(Res.string.err_openai_key)
            }
        }
        val id = UUID.randomUUID().toString()
        _tasks.update {
            it + TaskRecord(
                id = id,
                fileName = videoFile.name,
                sourcePath = videoFile.absolutePath,
                phase = PipelinePhase.Queued,
                message = JvmResourceStrings.text(Res.string.phase_queued),
            )
        }
        fileQueue.addLast(id to videoFile)
        startNextIfIdle()
        return null
    }

    fun retryTask(id: String): String? {
        return try {
            val task = _tasks.value.firstOrNull { it.id == id } ?: return null
            val src = task.sourcePath?.takeIf { it.isNotBlank() } ?: return JvmResourceStrings.text(
                Res.string.err_file_read,
                task.fileName,
            )
            val file = File(src)
            if (!file.isFile) {
                return JvmResourceStrings.text(Res.string.err_file_read, src)
            }
            val samePath = _tasks.value.filter { it.sourcePath == src }
            for (t in samePath) {
                fileQueue.removeAll { it.first == t.id }
                if (currentRunningTaskId == t.id) {
                    pipelineJob?.cancel()
                }
            }
            _tasks.update { list -> list.filterNot { it.sourcePath == src } }
            enqueuePipeline(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JvmResourceStrings.text(Res.string.err_retry_failed, e.message ?: e.toString())
        }
    }

    fun cancelTask(id: String) {
        fileQueue.removeAll { it.first == id }
        if (currentRunningTaskId == id) {
            pipelineJob?.cancel()
        } else {
            updateTask(id) {
                if (it.phase == PipelinePhase.Queued) {
                    it.copy(
                        phase = PipelinePhase.Cancelled,
                        message = JvmResourceStrings.text(Res.string.phase_cancelled),
                    )
                } else {
                    it
                }
            }
        }
    }

    fun deleteTask(id: String) {
        fileQueue.removeAll { it.first == id }
        if (currentRunningTaskId == id) {
            pipelineJob?.cancel()
        }
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun startAllTasks(): String? {
        val alreadyQueued = fileQueue.map { it.first }.toSet()
        val restartable = _tasks.value.filter {
            it.phase in setOf(PipelinePhase.Queued, PipelinePhase.Cancelled, PipelinePhase.Failed)
                    && !it.sourcePath.isNullOrBlank()
                    && it.id !in alreadyQueued
        }
        var missingCount = 0
        for (task in restartable) {
            val src = task.sourcePath ?: continue
            val file = File(src)
            if (!file.isFile) {
                missingCount++
                updateTask(task.id) {
                    it.copy(
                        phase = PipelinePhase.Failed,
                        message = "",
                        progress = 0f,
                        error = JvmResourceStrings.text(Res.string.err_file_read, src),
                        translationStats = null,
                    )
                }
                continue
            }
            updateTask(task.id) {
                it.copy(phase = PipelinePhase.Queued, progress = 0f, message = "", error = null, translationStats = null)
            }
            fileQueue.addLast(task.id to file)
        }
        startNextIfIdle()
        return if (missingCount > 0) {
            JvmResourceStrings.text(Res.string.warn_start_all_missing_files, missingCount)
        } else {
            null
        }
    }

    fun pauseAllTasks() {
        fileQueue.clear()
        _tasks.update { list ->
            list.map { task ->
                if (task.phase.isCancellableByStopAll()) {
                    task.copy(
                        phase = PipelinePhase.Cancelled,
                        message = JvmResourceStrings.text(Res.string.phase_cancelled),
                        progress = 0f,
                    )
                } else {
                    task
                }
            }
        }
        pipelineJob?.cancel()
    }

    fun deleteAllTasks() {
        fileQueue.clear()
        pipelineJob?.cancel()
        _tasks.update { emptyList() }
    }

    private fun startNextIfIdle() {
        if (pipelineJob?.isActive == true) return
        val next = fileQueue.removeFirstOrNull() ?: return
        val (id, file) = next
        pipelineJob = viewModelScope.launch(Dispatchers.Default) {
            val myJob = coroutineContext[Job]!!
            try {
                runPipelineInternal(id, file)
            } catch (e: CancellationException) {
                updateTask(id) {
                    it.copy(
                        phase = PipelinePhase.Cancelled,
                        message = JvmResourceStrings.text(Res.string.phase_cancelled),
                        progress = 0f,
                    )
                }
                throw e
            } finally {
                if (currentRunningTaskId == id) currentRunningTaskId = null
                if (pipelineJob === myJob) {
                    pipelineJob = null
                    startNextIfIdle()
                }
            }
        }
    }

    private fun updateTask(id: String, transform: (TaskRecord) -> TaskRecord) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    /** 任务已为 Cancelled 时不再被管道进度覆盖（例如全部停止后子进程尚未退出） */
    private fun updateTaskFromPipeline(id: String, transform: (TaskRecord) -> TaskRecord) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id != id) return@map task
                if (task.phase == PipelinePhase.Cancelled) return@map task
                transform(task)
            }
        }
    }

    private suspend fun runPipelineInternal(id: String, file: File) {
        currentRunningTaskId = id
        val cfg = _tooling.value

        val listener = object : PipelineListener {
            override fun onStateChange(phase: PipelinePhase, message: String, progress: Float) {
                updateTaskFromPipeline(id) {
                    it.copy(phase = phase, message = message, progress = progress)
                }
            }

            override fun onProgress(message: String, progress: Float?) {
                updateTaskFromPipeline(id) {
                    val p = progress ?: it.progress
                    it.copy(message = message, progress = p)
                }
            }

            override fun onCompleted(outputPath: String?, translationStats: TranslationTaskStats?) {
                updateTaskFromPipeline(id) {
                    it.copy(
                        phase = PipelinePhase.Done,
                        message = JvmResourceStrings.text(Res.string.phase_done_short),
                        progress = 1f,
                        outputPath = outputPath ?: it.outputPath,
                        error = null,
                        translationStats = translationStats
                    )
                }
            }

            override fun onError(error: String) {
                updateTaskFromPipeline(id) {
                    it.copy(
                        phase = PipelinePhase.Failed,
                        message = "",
                        progress = 0f,
                        error = error,
                        translationStats = null
                    )
                }
            }
        }

        PipelineEngine.execute(id, file, cfg, listener)
    }
}