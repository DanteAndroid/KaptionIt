package com.danteandroid.transbee.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danteandroid.transbee.process.PipelineEngine
import com.danteandroid.transbee.process.PipelineListener
import com.danteandroid.transbee.process.PipelinePhase
import com.danteandroid.transbee.process.isCancellableByStopAll
import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.settings.ToolingSettingsStore
import com.danteandroid.transbee.settings.TaskRecordStore
import com.danteandroid.transbee.settings.TranscriptionCacheStore
import com.danteandroid.transbee.translate.AppleTranslateBinary
import com.danteandroid.transbee.translate.TranslationEngine
import com.danteandroid.transbee.utils.JvmResourceStrings
import com.danteandroid.transbee.utils.OsUtils
import com.danteandroid.transbee.utils.toReadableByteSize
import com.danteandroid.transbee.whisper.WhisperModelDownloader
import com.danteandroid.transbee.whisper.WhisperModelOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.duration_format_min_sec
import transbee.composeapp.generated.resources.duration_format_sec_only
import transbee.composeapp.generated.resources.err_apple_translate_macos_only
import transbee.composeapp.generated.resources.err_apple_translate_missing
import transbee.composeapp.generated.resources.err_deepl_key
import transbee.composeapp.generated.resources.err_file_read
import transbee.composeapp.generated.resources.err_gemini_key
import transbee.composeapp.generated.resources.err_google_key
import transbee.composeapp.generated.resources.err_mineru_token_missing
import transbee.composeapp.generated.resources.err_openai_key
import transbee.composeapp.generated.resources.err_queue_while_downloading
import transbee.composeapp.generated.resources.err_retry_failed
import transbee.composeapp.generated.resources.err_unsupported_file_type
import transbee.composeapp.generated.resources.err_whisper_model_missing
import transbee.composeapp.generated.resources.msg_download_complete
import transbee.composeapp.generated.resources.msg_download_connecting
import transbee.composeapp.generated.resources.msg_download_reconnecting
import transbee.composeapp.generated.resources.msg_download_skipped_local
import transbee.composeapp.generated.resources.msg_total_duration
import transbee.composeapp.generated.resources.phase_cancelled
import transbee.composeapp.generated.resources.phase_done_short
import transbee.composeapp.generated.resources.phase_queued
import transbee.composeapp.generated.resources.warn_start_all_missing_files
import java.io.File
import java.util.UUID

private val mineruDocExtensions = setOf(
    "pdf", "doc", "docx", "ppt", "pptx",
    "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp",
)
private val textExtensions = setOf("txt", "md")
private val mediaExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv",
    "mp3", "wav", "aac", "flac", "m4a", "ogg", "wma",
)

class PipelineViewModel : ViewModel() {

    private val _tooling = MutableStateFlow(ToolingSettingsStore.loadOrDefault())
    val tooling: StateFlow<ToolingSettings> = _tooling.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskRecord>>(TaskRecordStore.loadTasks())
    val tasks: StateFlow<List<TaskRecord>> = _tasks.asStateFlow()

    private val _modelDownload = MutableStateFlow(ModelDownloadUiState())
    val modelDownload: StateFlow<ModelDownloadUiState> = _modelDownload.asStateFlow()

    private val fileQueue = ArrayDeque<Pair<String, File>>()
    private val pipelineStartLock = Any()
    private var pipelineJob: Job? = null
    private var currentRunningTaskId: String? = null
    private var modelDownloadJob: Job? = null
    private var pendingTasksPersistJob: Job? = null

    private var lastAutoOpenTaskId: String? = null


    fun clearTranscriptionCache() {
        TranscriptionCacheStore.clearAll()
    }

    fun updateTooling(transform: (ToolingSettings) -> ToolingSettings) {
        _tooling.update(transform)
        ToolingSettingsStore.save(_tooling.value)
    }

    fun downloadWhisperModel(option: WhisperModelOption, forceRedownload: Boolean) {
        if (_modelDownload.value.active) return
        var lastModelDownloadUiMs = 0L
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
                        val complete = total != null && total > 0L && received >= total
                        val now = System.currentTimeMillis()
                        if (complete || now - lastModelDownloadUiMs >= 120L) {
                            lastModelDownloadUiMs = now
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
                        }
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

    fun missingKeyMessageForEngine(engine: TranslationEngine, cfg: ToolingSettings): String? =
        when (engine) {
            TranslationEngine.APPLE -> {
                when {
                    !OsUtils.isMacOs() -> JvmResourceStrings.text(Res.string.err_apple_translate_macos_only)
                    AppleTranslateBinary.resolvePath(cfg.appleTranslateBinary) == null ->
                        JvmResourceStrings.text(Res.string.err_apple_translate_missing)

                    else -> null
                }
            }

            TranslationEngine.GOOGLE ->
                if (cfg.googleApiKey.isBlank()) JvmResourceStrings.text(Res.string.err_google_key) else null

            TranslationEngine.DEEPL ->
                if (cfg.deeplApiKey.isBlank()) JvmResourceStrings.text(Res.string.err_deepl_key) else null

            TranslationEngine.GEMINI ->
                if (cfg.geminiApiKey.isBlank()) JvmResourceStrings.text(Res.string.err_gemini_key) else null

            TranslationEngine.OPENAI ->
                if (cfg.openAiKey.isBlank()) JvmResourceStrings.text(Res.string.err_openai_key) else null
        }

    private fun validateTranslationEngine(cfg: ToolingSettings): String? =
        missingKeyMessageForEngine(cfg.translationEngine, cfg)

    fun onFilesSelected(files: List<File>): List<String> {
        if (files.isEmpty()) return emptyList()
        val isSingleFileBatch = files.size == 1
        val errors = mutableListOf<String>()
        files.forEach { file ->
            val result = enqueuePipeline(file)
            if (isTaskId(result)) {
                if (isSingleFileBatch) {
                    val ext = file.extension.lowercase()
                    lastAutoOpenTaskId = if (ext in mineruDocExtensions || ext in textExtensions) {
                        result
                    } else {
                        null
                    }
                }
            } else {
                errors.add(result)
            }
        }
        return errors
    }

    private fun isTaskId(s: String): Boolean = runCatching { UUID.fromString(s) }.isSuccess

    fun enqueuePipeline(videoFile: File): String {
        if (!videoFile.isFile) {
            return JvmResourceStrings.text(Res.string.err_file_read, videoFile.path)
        }
        val cfg = _tooling.value
        val ext = videoFile.extension.lowercase()

        when {
            ext in mineruDocExtensions -> {
                if (cfg.minerUToken.isBlank()) {
                    return JvmResourceStrings.text(Res.string.err_mineru_token_missing)
                }
            }

            ext in textExtensions -> {
                // txt/md 直接走翻译，只需翻译引擎校验
                validateTranslationEngine(cfg)?.let { return it }
            }

            ext in mediaExtensions -> {
                if (_modelDownload.value.active) {
                    return JvmResourceStrings.text(Res.string.err_queue_while_downloading)
                }
                val modelPath = cfg.whisperModel.trim()
                if (modelPath.isEmpty() || !File(modelPath).isFile) {
                    return JvmResourceStrings.text(Res.string.err_whisper_model_missing)
                }
                validateTranslationEngine(cfg)?.let { return it }
            }

            else -> return JvmResourceStrings.text(Res.string.err_unsupported_file_type)
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
        persistTasksImmediate()
        return id
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
            enqueuePipeline(file).let { if (isTaskId(it)) null else it }
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
        persistTasksImmediate()
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
        persistTasksImmediate()
    }

    fun deleteAllTasks() {
        fileQueue.clear()
        pipelineJob?.cancel()
        _tasks.update { emptyList() }
        persistTasksImmediate()
    }

    private fun startNextIfIdle() {
        synchronized(pipelineStartLock) {
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
                    val shouldStartNext = synchronized(pipelineStartLock) {
                        if (pipelineJob === myJob) {
                            pipelineJob = null
                            true
                        } else {
                            false
                        }
                    }
                    if (shouldStartNext) startNextIfIdle()
                }
            }
        }
    }

    private fun persistTasksImmediate() {
        pendingTasksPersistJob?.cancel()
        pendingTasksPersistJob = null
        TaskRecordStore.saveTasks(_tasks.value)
    }

    private fun schedulePersistTasksDebounced() {
        pendingTasksPersistJob?.cancel()
        pendingTasksPersistJob = viewModelScope.launch {
            delay(320L)
            TaskRecordStore.saveTasks(_tasks.value)
        }
    }

    private fun updateTask(id: String, transform: (TaskRecord) -> TaskRecord) {
        _tasks.update { list ->
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return@update list
            val old = list[idx]
            val new = transform(old)
            if (new == old) return@update list
            list.toMutableList().also { it[idx] = new }
        }
        schedulePersistTasksDebounced()
    }

    /** 任务已为 Cancelled 时不再被管道进度覆盖（例如全部停止后子进程尚未退出） */
    private fun updateTaskFromPipeline(id: String, transform: (TaskRecord) -> TaskRecord) {
        _tasks.update { list ->
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return@update list
            val old = list[idx]
            if (old.phase == PipelinePhase.Cancelled) return@update list
            val new = transform(old)
            if (new == old) return@update list
            list.toMutableList().also { it[idx] = new }
        }
        schedulePersistTasksDebounced()
    }

    override fun onCleared() {
        pendingTasksPersistJob?.cancel()
        TaskRecordStore.saveTasks(_tasks.value)
        super.onCleared()
    }

    private suspend fun runPipelineInternal(id: String, file: File) {
        currentRunningTaskId = id
        val cfg = _tooling.value

        val listener = object : PipelineListener {
            override fun onStateChange(
                phase: PipelinePhase,
                message: String,
                progress: Float,
                indeterminate: Boolean
            ) {
                updateTaskFromPipeline(id) {
                    it.copy(
                        phase = phase,
                        message = message,
                        progress = progress.coerceIn(0f, 1f),
                        progressIndeterminate = indeterminate,
                    )
                }
            }

            override fun onProgress(message: String, progress: Float?, indeterminate: Boolean) {
                updateTaskFromPipeline(id) {
                    it.copy(
                        message = message,
                        progress = progress?.coerceIn(0f, 1f) ?: it.progress,
                        progressIndeterminate = indeterminate,
                    )
                }
            }

            override fun onCompleted(outputPath: String?, translationStats: TranslationTaskStats?) {
                updateTaskFromPipeline(id) {
                    val durationMs = System.currentTimeMillis() - it.createdAtMs
                    val totalSec = ((durationMs + 500) / 1000).coerceAtLeast(1)
                    val m = (totalSec / 60).toInt()
                    val s = (totalSec % 60).toInt()
                    val durationStr = if (m > 0) {
                        JvmResourceStrings.text(Res.string.duration_format_min_sec, m, s)
                    } else {
                        JvmResourceStrings.text(Res.string.duration_format_sec_only, s)
                    }

                    val updated = it.copy(
                        phase = PipelinePhase.Done,
                        message = if (translationStats == null) {
                            JvmResourceStrings.text(Res.string.msg_total_duration, durationStr)
                        } else {
                            JvmResourceStrings.text(Res.string.phase_done_short)
                        },
                        progress = 1f,
                        progressIndeterminate = false,
                        outputPath = outputPath ?: it.outputPath,
                        error = null,
                        translationStats = translationStats,
                        completedAtMs = System.currentTimeMillis(),
                    )
                    if (id == lastAutoOpenTaskId && updated.outputPath != null) {
                        OsUtils.openFile(File(updated.outputPath))
                        lastAutoOpenTaskId = null
                    }
                    updated
                }
            }

            override fun onError(error: String) {
                System.err.println("Pipeline Error [Task $id, File ${file.name}]: $error")
                updateTaskFromPipeline(id) {
                    it.copy(
                        phase = PipelinePhase.Failed,
                        message = "",
                        progress = 0f,
                        progressIndeterminate = false,
                        error = error,
                        translationStats = null
                    )
                }
            }
        }

        try {
            PipelineEngine.execute(id, file, cfg, listener)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            System.err.println("Pipeline uncaught [Task $id, File ${file.name}]: ${e.message}")
            updateTaskFromPipeline(id) {
                it.copy(
                    phase = PipelinePhase.Failed,
                    message = "",
                    progress = 0f,
                    progressIndeterminate = false,
                    error = e.message ?: e.toString(),
                    translationStats = null,
                )
            }
        }
    }
}
