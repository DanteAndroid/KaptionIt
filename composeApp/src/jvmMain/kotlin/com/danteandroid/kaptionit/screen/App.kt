@file:OptIn(ExperimentalMaterial3Api::class)

package com.danteandroid.transbee.screen

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danteandroid.transbee.AppLanguage
import com.danteandroid.transbee.AppLocale
import com.danteandroid.transbee.AppTheme
import com.danteandroid.transbee.TransbeeTheme
import com.danteandroid.transbee.ui.PipelineViewModel
import com.danteandroid.transbee.ui.labelRes
import com.danteandroid.transbee.utils.SmokeTestResult
import com.danteandroid.transbee.utils.runServiceSmokeTest
import com.danteandroid.transbee.utils.smokeTestSourceText
import com.danteandroid.transbee.whisper.WhisperModelCatalog
import com.danteandroid.transbee.whisper.isDownloaded
import com.danteandroid.transbee.whisper.modelFile
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.action_close
import transbee.composeapp.generated.resources.action_run_test
import transbee.composeapp.generated.resources.dialog_translation_test_current_engine
import transbee.composeapp.generated.resources.dialog_translation_test_elapsed_ms
import transbee.composeapp.generated.resources.dialog_translation_test_hint_tap_run
import transbee.composeapp.generated.resources.dialog_translation_test_running
import transbee.composeapp.generated.resources.dialog_translation_test_title
import transbee.composeapp.generated.resources.subtitle_output_source
import transbee.composeapp.generated.resources.subtitle_output_target

@Composable
fun App() {
    var appLanguage by remember { mutableStateOf(AppLanguage.ZH) }
    val viewModel: PipelineViewModel = viewModel { PipelineViewModel() }
    val tooling by viewModel.tooling.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val modelDl by viewModel.modelDownload.collectAsState()
    val presets = remember { WhisperModelCatalog.presets }
    var selectedPreset by remember {
        mutableStateOf(presets.firstOrNull { it.id == "base" } ?: presets.first())
    }
    var showSetting by remember { mutableStateOf(false) }
    var showTranslationTestDialog by remember { mutableStateOf(false) }
    var serviceTestBusy by remember { mutableStateOf(false) }
    var serviceTestResult by remember { mutableStateOf<SmokeTestResult?>(null) }
    var serviceTestError by remember { mutableStateOf<String?>(null) }
    var serviceTestElapsedMs by remember { mutableStateOf(0L) }

    LaunchedEffect(presets, tooling.whisperModel) {
        val path = tooling.whisperModel
        if (path.isBlank()) return@LaunchedEffect
        val match = presets.firstOrNull { path.endsWith(it.fileName) }
        if (match != null) selectedPreset = match
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var prevModelDlActive by remember { mutableStateOf(false) }
    LaunchedEffect(modelDl.active) {
        if (prevModelDlActive && !modelDl.active) {
            val text = modelDl.error ?: modelDl.message
            if (text.isNotEmpty()) scope.launch { snackbarHostState.showSnackbar(text) }
        }
        prevModelDlActive = modelDl.active
    }

    LaunchedEffect(showTranslationTestDialog) {
        if (showTranslationTestDialog) {
            serviceTestResult = null
            serviceTestError = null
            serviceTestElapsedMs = 0L
        }
    }

    TransbeeTheme {
        val spacing = AppTheme.spacing
        // 语言切换后强制重组，使界面字符串立即随 AppLocale 更新
        key(appLanguage) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
            ) { padding ->
                Column(Modifier.padding(padding)) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        SettingsPanel(
                            selectedPreset = selectedPreset,
                            modelDl = modelDl,
                            tooling = tooling,
                            viewModel = viewModel,
                            onSelectPreset = { selectedPreset = it },
                            onTranslationEngineKeyHint = { msg ->
                                if (msg != null) scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            modifier = Modifier.weight(0.34f).fillMaxHeight(),
                        )
                        VerticalDivider(Modifier.width(1.dp).fillMaxHeight())
                        Box(
                            Modifier
                                .weight(0.66f)
                                .fillMaxHeight()
                                .padding(spacing.large),
                        ) {
                            AppTaskScreen(
                                tasks = tasks,
                                onFilesSelected = { files ->
                                    val errs =
                                        viewModel.onFilesSelected(files).filter { it.isNotBlank() }
                                    if (errs.isNotEmpty()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = errs.joinToString("\n"),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                onDeleteTask = viewModel::deleteTask,
                                onRetryTask = { id ->
                                    val err = viewModel.retryTask(id)
                                    if (err != null) scope.launch {
                                        snackbarHostState.showSnackbar(
                                            err
                                        )
                                    }
                                },
                                onStartAll = {
                                    val err = viewModel.startAllTasks()
                                    if (err != null) scope.launch {
                                        snackbarHostState.showSnackbar(
                                            err
                                        )
                                    }
                                },
                                onPauseAll = viewModel::pauseAllTasks,
                                onDeleteAll = viewModel::deleteAllTasks,
                                modifier = Modifier.fillMaxSize(),
                            )
                            SnackbarHost(
                                snackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                    StatusBarRow(
                        selectedPresetId = selectedPreset.id,
                        modelDownload = modelDl,
                        onTranslationTestClick = { showTranslationTestDialog = true },
                        onSettingsClick = { showSetting = true },
                    )
                }
            }
        }

        if (showSetting) {
            AppSettingDialog(
                tooling = tooling,
                onUpdateTooling = viewModel::updateTooling,
                onDismissRequest = { showSetting = false },
                onLocaleZh = { appLanguage = AppLanguage.ZH; AppLocale.apply(AppLanguage.ZH) },
                onLocaleEn = { appLanguage = AppLanguage.EN; AppLocale.apply(AppLanguage.EN) },
                onClearTranscriptionCache = viewModel::clearTranscriptionCache,
            )
        }

        if (showTranslationTestDialog) {
            val engineName = stringResource(tooling.translationEngine.labelRes)
            TranslationTestDialog(
                engineName = engineName,
                previewOriginal = smokeTestSourceText(tooling),
                busy = serviceTestBusy,
                result = serviceTestResult,
                error = serviceTestError,
                elapsedMs = serviceTestElapsedMs,
                onRun = {
                    scope.launch {
                        serviceTestBusy = true
                        serviceTestResult = null
                        serviceTestError = null
                        try {
                            val t0 = System.currentTimeMillis()
                            serviceTestResult = runServiceSmokeTest(tooling)
                            serviceTestElapsedMs = System.currentTimeMillis() - t0
                        } catch (e: Exception) {
                            serviceTestError = e.message ?: e.toString()
                        } finally {
                            serviceTestBusy = false
                        }
                    }
                },
                onDismiss = { if (!serviceTestBusy) showTranslationTestDialog = false },
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    selectedPreset: com.danteandroid.transbee.whisper.WhisperModelOption,
    modelDl: com.danteandroid.transbee.ui.ModelDownloadUiState,
    tooling: com.danteandroid.transbee.settings.ToolingSettings,
    viewModel: PipelineViewModel,
    onSelectPreset: (com.danteandroid.transbee.whisper.WhisperModelOption) -> Unit,
    onTranslationEngineKeyHint: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    Box(modifier) {
        val scrollState = rememberScrollState()
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            ModelSettingCard(
                selectedPreset = selectedPreset,
                modelDownload = modelDl,
                whisperLanguage = tooling.whisperLanguage,
                onWhisperLanguageChange = { code -> viewModel.updateTooling { it.copy(whisperLanguage = code) } },
                whisperVadEnabled = tooling.whisperVadEnabled,
                onWhisperVadChange = { v -> viewModel.updateTooling { it.copy(whisperVadEnabled = v) } },
                onSelectModel = { opt ->
                    if (opt.isDownloaded()) {
                        onSelectPreset(opt)
                        viewModel.updateTooling { it.copy(whisperModel = opt.modelFile().absolutePath) }
                    }
                },
                onDownloadModel = { viewModel.downloadWhisperModel(it, false) },
                onStopDownload = viewModel::cancelModelDownload,
            )
            TranslationSettingCard(
                tooling = tooling,
                onUpdateTooling = viewModel::updateTooling,
                onTranslationEngineChanged = { eng ->
                    onTranslationEngineKeyHint(viewModel.missingKeyMessageForEngine(eng, tooling))
                },
            )
            ExportSettingCard(tooling = tooling, onUpdateTooling = viewModel::updateTooling)
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun TranslationTestDialog(
    engineName: String,
    previewOriginal: String,
    busy: Boolean,
    result: SmokeTestResult?,
    error: String?,
    elapsedMs: Long,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = AppTheme.spacing
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_translation_test_title)) },
        text = {
            Column(
                Modifier.height(400.dp).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                Text(
                    stringResource(Res.string.dialog_translation_test_current_engine, engineName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busy) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            )
                            Text(
                                stringResource(Res.string.dialog_translation_test_running),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                Text(
                    stringResource(Res.string.subtitle_output_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    result?.original ?: previewOriginal,
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (result != null) {
                    Text(
                        stringResource(Res.string.subtitle_output_target),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(result.translated, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(Res.string.dialog_translation_test_elapsed_ms, elapsedMs.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!busy && result == null && error == null) {
                    Text(
                        stringResource(Res.string.dialog_translation_test_hint_tap_run),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.action_close)) }
        },
        confirmButton = {
            Button(onClick = onRun, enabled = !busy) { Text(stringResource(Res.string.action_run_test)) }
        },
    )
}
