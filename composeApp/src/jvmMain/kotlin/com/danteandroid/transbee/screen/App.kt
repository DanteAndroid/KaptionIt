@file:OptIn(ExperimentalMaterial3Api::class)

package com.danteandroid.transbee.screen

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danteandroid.transbee.AppLanguage
import com.danteandroid.transbee.AppLocale
import com.danteandroid.transbee.AppTheme
import com.danteandroid.transbee.TransbeeTheme
import com.danteandroid.transbee.feishu.FeishuKeyManager
import com.danteandroid.transbee.feishu.PurchaseNotVerifiedException
import com.danteandroid.transbee.process.PipelinePhase
import com.danteandroid.transbee.settings.mergePurchasedConfiguration
import com.danteandroid.transbee.ui.AppVerticalScrollbar
import com.danteandroid.transbee.ui.PipelineViewModel
import com.danteandroid.transbee.ui.labelRes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import transbee.composeapp.generated.resources.*
import transbee.composeapp.generated.resources.Res
import com.danteandroid.transbee.utils.DeviceIdentity
import com.danteandroid.transbee.utils.JvmResourceStrings
import com.danteandroid.transbee.utils.SmokeTestResult
import com.danteandroid.transbee.utils.TRANSLATION_SERVICE_DEFAULT_SOURCE_EN
import com.danteandroid.transbee.utils.runServiceSmokeTest
import com.danteandroid.transbee.whisper.WhisperModelCatalog
import com.danteandroid.transbee.whisper.isDownloaded
import com.danteandroid.transbee.whisper.modelFile
import kotlinx.coroutines.launch
import transbee.composeapp.generated.resources.action_test_translation
import transbee.composeapp.generated.resources.msg_requesting
import transbee.composeapp.generated.resources.msg_translation_service_configured
import transbee.composeapp.generated.resources.dialog_translation_service_title
import transbee.composeapp.generated.resources.translation_service_current_info
import transbee.composeapp.generated.resources.dialog_translation_test_elapsed_ms
import transbee.composeapp.generated.resources.purchase_transfer_line1
import transbee.composeapp.generated.resources.purchase_transfer_line2
import transbee.composeapp.generated.resources.purchase_dialog_title
import transbee.composeapp.generated.resources.purchase_i_paid
import transbee.composeapp.generated.resources.purchase_not_verified
import transbee.composeapp.generated.resources.wechat_pay
import transbee.composeapp.generated.resources.subtitle_output_source
import transbee.composeapp.generated.resources.subtitle_output_target

@Composable
fun App(topWindowInset: Dp = 0.dp) {
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
    var showTranslationServiceDialog by remember { mutableStateOf(false) }
    var purchaseBusy by remember { mutableStateOf(false) }
    var purchaseError by remember { mutableStateOf<String?>(null) }
    val deviceId = remember { DeviceIdentity.getStableDeviceId() }
    var serviceTestBusy by remember { mutableStateOf(false) }
    var serviceTestResult by remember { mutableStateOf<SmokeTestResult?>(null) }
    var serviceTestError by remember { mutableStateOf<String?>(null) }
    var serviceTestElapsedMs by remember { mutableStateOf(0L) }
    var serviceTestInputText by remember { mutableStateOf("") }

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

    LaunchedEffect(showTranslationServiceDialog) {
        if (showTranslationServiceDialog) {
            purchaseError = null
            serviceTestResult = null
            serviceTestError = null
            serviceTestElapsedMs = 0L
            serviceTestInputText = TRANSLATION_SERVICE_DEFAULT_SOURCE_EN
        }
    }

    TransbeeTheme(darkTheme = true) {
        val spacing = AppTheme.spacing
        key(appLanguage) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topWindowInset),
                    containerColor = Color.Transparent,
                ) { padding ->
            BoxWithConstraints(
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                val isWideEnough = maxWidth >= 560.dp
                val sidebarVisible = isWideEnough && tooling.sidebarExpanded
                val currentMaxWidth = maxWidth
                Row(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = sidebarVisible,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut(),
                    ) {
                        Row(modifier = Modifier.fillMaxHeight().width(currentMaxWidth * 0.32f)) {
                            SettingsPanel(
                                selectedPreset = selectedPreset,
                                modelDl = modelDl,
                                tooling = tooling,
                                viewModel = viewModel,
                                onSelectPreset = { selectedPreset = it },
                                onTranslationEngineKeyHint = { msg ->
                                    if (msg != null) scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
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
                                        snackbarHostState.showSnackbar(err)
                                    }
                                },
                                onStartAll = {
                                    val err = viewModel.startAllTasks()
                                    if (err != null) scope.launch {
                                        snackbarHostState.showSnackbar(err)
                                    }
                                },
                                onPauseAll = viewModel::pauseAllTasks,
                                onDeleteAll = viewModel::deleteAllTasks,
                                modifier = Modifier.fillMaxSize(),
                            )
                            
                            // Manual handle to expand the sidebar when it's wide enough but manually collapsed
                            if (isWideEnough && !tooling.sidebarExpanded) {
                                IconButton(
                                    onClick = { viewModel.updateTooling { it.copy(sidebarExpanded = true) } },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = spacing.medium, start = spacing.large)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardDoubleArrowRight,
                                        contentDescription = stringResource(Res.string.tooltip_expand_sidebar),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            SnackbarHost(
                                snackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        StatusBarRow(
                            modelDownload = modelDl,
                            onTranslationServiceClick = { showTranslationServiceDialog = true },
                            onSettingsClick = { showSetting = true },
                        )
                    }
                }
            }
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

        if (showTranslationServiceDialog) {
            val engineName = stringResource(tooling.translationEngine.labelRes)
            TranslationServiceDialog(
                deviceId = deviceId,
                purchaseBusy = purchaseBusy,
                purchaseError = purchaseError,
                onConfirmPurchase = {
                    scope.launch {
                        purchaseBusy = true
                        purchaseError = null
                        try {
                            val key = FeishuKeyManager.verifyPurchaseAndFetchKey(deviceId)
                            viewModel.updateTooling { it.mergePurchasedConfiguration(key) }
                            showTranslationServiceDialog = false
                            snackbarHostState.showSnackbar(
                                JvmResourceStrings.text(Res.string.msg_translation_service_configured),
                            )
                        } catch (_: PurchaseNotVerifiedException) {
                            purchaseError = JvmResourceStrings.text(Res.string.purchase_not_verified)
                        } catch (e: Exception) {
                            purchaseError = e.message ?: e.toString()
                        } finally {
                            purchaseBusy = false
                        }
                    }
                },
                engineName = engineName,
                inputText = serviceTestInputText,
                onInputTextChange = { serviceTestInputText = it },
                serviceTestBusy = serviceTestBusy,
                serviceTestResult = serviceTestResult,
                serviceTestError = serviceTestError,
                serviceTestElapsedMs = serviceTestElapsedMs,
                onRunTest = {
                    scope.launch {
                        serviceTestBusy = true
                        serviceTestResult = null
                        serviceTestError = null
                        try {
                            val t0 = System.currentTimeMillis()
                            serviceTestResult = runServiceSmokeTest(tooling, serviceTestInputText)
                            serviceTestElapsedMs = System.currentTimeMillis() - t0
                        } catch (e: Exception) {
                            serviceTestError = e.message ?: e.toString()
                        } finally {
                            serviceTestBusy = false
                        }
                    }
                },
                onDismiss = {
                    if (!purchaseBusy && !serviceTestBusy) showTranslationServiceDialog = false
                },
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
    val scope = rememberCoroutineScope()
    
    Box(modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = spacing.medium, bottom = 14.dp) // Fixed symmetric top (12dp) and 14dp bottom
                .verticalScroll(scrollState)
                .padding(horizontal = spacing.large)
                .padding(end = 8.dp), // Space for scrollbar
            verticalArrangement = Arrangement.spacedBy(spacing.xxLarge),
        ) {
            SettingsSection(
                title = stringResource(Res.string.section_model_settings),
                action = {
                    IconButton(
                        onClick = { viewModel.updateTooling { it.copy(sidebarExpanded = false) } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.KeyboardDoubleArrowLeft,
                            contentDescription = stringResource(Res.string.tooltip_collapse_sidebar),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
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
            }

            SettingsSection(stringResource(Res.string.section_translation)) {
                TranslationSettingCard(
                    tooling = tooling,
                    onUpdateTooling = viewModel::updateTooling,
                    onTranslationEngineChanged = { eng ->
                        onTranslationEngineKeyHint(viewModel.missingKeyMessageForEngine(eng, tooling))
                    },
                )
            }

            SettingsSection(stringResource(Res.string.section_export)) {
                ExportSettingCard(tooling = tooling, onUpdateTooling = viewModel::updateTooling)
            }

        }

        com.danteandroid.transbee.ui.AppVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val spacing = AppTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun TranslationServiceDialog(
    deviceId: String,
    purchaseBusy: Boolean,
    purchaseError: String?,
    onConfirmPurchase: () -> Unit,
    engineName: String,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    serviceTestBusy: Boolean,
    serviceTestResult: SmokeTestResult?,
    serviceTestError: String?,
    serviceTestElapsedMs: Long,
    onRunTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = AppTheme.spacing
    val scrollState = rememberScrollState()
    val anyBusy = purchaseBusy || serviceTestBusy
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.dialog_translation_service_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Box(Modifier.heightIn(max = 560.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = spacing.large, end = 24.dp), // Match sidebar gap
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    Text(
                        stringResource(Res.string.purchase_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                    Text(
                        stringResource(Res.string.purchase_transfer_line2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.small),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.medium),
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.wechat_pay),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                            )
                            SelectionContainer {
                                Text(
                                    stringResource(Res.string.purchase_transfer_line1, deviceId),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = onConfirmPurchase, enabled = !purchaseBusy) {
                                    Text(stringResource(Res.string.purchase_i_paid))
                                }
                            }
                        }
                        if (purchaseError != null) {
                            SelectionContainer {
                                Text(
                                    purchaseError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(Res.string.translation_service_current_info, engineName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                    Text(
                        stringResource(Res.string.subtitle_output_source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 120.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        enabled = !serviceTestBusy,
                        maxLines = 3,
                    )
                    if (serviceTestError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                    horizontal = spacing.medium,
                                    vertical = spacing.small,
                                ),
                            ) {
                                Text(
                                    serviceTestError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                val clipboard = LocalClipboardManager.current
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(serviceTestError)) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    if (serviceTestResult != null) {
                        HorizontalDivider()
                        Text(
                            stringResource(Res.string.subtitle_output_target),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(serviceTestResult.translated, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(Res.string.dialog_translation_test_elapsed_ms, serviceTestElapsedMs.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onRunTest,
                            enabled = !serviceTestBusy && inputText.isNotBlank(),
                        ) {
                            Text(
                                if (serviceTestBusy) {
                                    stringResource(Res.string.msg_requesting)
                                } else {
                                    stringResource(Res.string.action_test_translation)
                                },
                            )
                        }
                    }
                }
                com.danteandroid.transbee.ui.AppVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !anyBusy) {
                Text(stringResource(Res.string.action_close))
            }
        },
        confirmButton = {},
    )
}

