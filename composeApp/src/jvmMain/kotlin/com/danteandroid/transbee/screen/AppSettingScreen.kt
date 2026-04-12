package com.danteandroid.transbee.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danteandroid.transbee.AppTheme
import com.danteandroid.transbee.TransbeeTheme
import com.danteandroid.transbee.settings.PdfTranslateFormat
import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.translate.TranslationEngine
import com.danteandroid.transbee.ui.ExportFormat
import com.danteandroid.transbee.ui.ModelDownloadUiState
import com.danteandroid.transbee.ui.SubtitleOutputKind
import com.danteandroid.transbee.ui.labelRes
import kotlinx.coroutines.CoroutineScope
import com.danteandroid.transbee.ui.targetLanguageOptions
import com.danteandroid.transbee.ui.whisperTranscriptionLanguageOptions
import com.danteandroid.transbee.whisper.WhisperModelCatalog
import com.danteandroid.transbee.whisper.WhisperModelOption
import com.danteandroid.transbee.whisper.displayName
import com.danteandroid.transbee.whisper.isDownloaded
import org.jetbrains.compose.resources.stringResource
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.action_close
import transbee.composeapp.generated.resources.action_confirm
import transbee.composeapp.generated.resources.action_download
import transbee.composeapp.generated.resources.action_stop_download
import transbee.composeapp.generated.resources.dialog_vad_desc
import transbee.composeapp.generated.resources.label_markdown_format
import transbee.composeapp.generated.resources.label_recognition_model
import transbee.composeapp.generated.resources.label_subtitle_content
import transbee.composeapp.generated.resources.label_subtitle_format
import transbee.composeapp.generated.resources.label_target_language
import transbee.composeapp.generated.resources.label_translation_engine
import transbee.composeapp.generated.resources.label_vad_toggle
import transbee.composeapp.generated.resources.label_whisper_language
import transbee.composeapp.generated.resources.markdown_format_help_content
import transbee.composeapp.generated.resources.model_group_english_only
import transbee.composeapp.generated.resources.model_group_general
import transbee.composeapp.generated.resources.section_vad

private val translationEngineMenuOrder: List<TranslationEngine> = listOf(
    TranslationEngine.APPLE,
    TranslationEngine.GOOGLE,
    TranslationEngine.GEMINI,
    TranslationEngine.DEEPL,
    TranslationEngine.OPENAI,
)

private val panelCardColors
    @Composable
    get() =
            CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

@Composable
private fun PanelDropdownMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = cs.surfaceContainerHigh,
            border = BorderStroke(1.dp, cs.outlineVariant),
            content = content,
    )
}

@Composable
private fun SectionTitleWithIcon(icon: ImageVector, title: String) {
    val spacing = AppTheme.spacing
    Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary,
        )
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun ModelSettingCard(
        selectedPreset: WhisperModelOption,
        modelDownload: ModelDownloadUiState,
        whisperLanguage: String,
        onWhisperLanguageChange: (String) -> Unit,
        whisperVadEnabled: Boolean,
        onWhisperVadChange: (Boolean) -> Unit,
        onSelectModel: (WhisperModelOption) -> Unit,
        onDownloadModel: (WhisperModelOption) -> Unit,
        onStopDownload: () -> Unit,
        modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }
    var whisperLangExpanded by remember { mutableStateOf(false) }
    var showVadHelp by remember { mutableStateOf(false) }
    val menuTextStyle = MaterialTheme.typography.bodyMedium

    val density = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }

    Card(
            colors = panelCardColors,
            elevation = CardDefaults.cardElevation(spacing.xSmall),
            modifier = modifier.fillMaxWidth(),
    ) {
        Column(
                Modifier.padding(spacing.medium).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                    stringResource(Res.string.label_recognition_model),
                    style = MaterialTheme.typography.labelMedium
            )
            Box(
                    Modifier.fillMaxWidth().onGloballyPositioned {
                        menuWidth = with(density) { it.size.width.toDp() }
                    }
            ) {
                OutlinedButton(onClick = { menuExpanded = true }, Modifier.fillMaxWidth()) {
                    Text(selectedPreset.displayName)
                }
                PanelDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.width(menuWidth)
                ) {
                    Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                        ModelGroupHeader(stringResource(Res.string.model_group_general))
                        val showSize = menuWidth > 200.dp
                        WhisperModelMenuItems(
                                models = WhisperModelCatalog.presetsMain,
                                menuTextStyle = menuTextStyle,
                                modelDownload = modelDownload,
                                showSize = showSize,
                                onSelect = {
                                    menuExpanded = false
                                    onSelectModel(it)
                                },
                                onDownload = {
                                    menuExpanded = false
                                    onDownloadModel(it)
                                },
                                onStopDownload = onStopDownload,
                        )
                        HorizontalDivider()
                        Spacer(Modifier.size(spacing.large))
                        ModelGroupHeader(stringResource(Res.string.model_group_english_only))
                        WhisperModelMenuItems(
                                models = WhisperModelCatalog.presetsEnglishOnly,
                                menuTextStyle = menuTextStyle,
                                modelDownload = modelDownload,
                                showSize = showSize,
                                onSelect = {
                                    menuExpanded = false
                                    onSelectModel(it)
                                },
                                onDownload = {
                                    menuExpanded = false
                                    onDownloadModel(it)
                                },
                                onStopDownload = onStopDownload,
                        )
                    }
                }
            }
            Text(
                    stringResource(Res.string.label_whisper_language),
                    style = MaterialTheme.typography.labelMedium
            )
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { whisperLangExpanded = true }, Modifier.fillMaxWidth()) {
                    val code = whisperLanguage.trim().lowercase().ifEmpty { "auto" }
                    val langLabel =
                            whisperTranscriptionLanguageOptions.firstOrNull {
                                it.whisperCode == code
                            }
                    Text(
                            if (langLabel != null) stringResource(langLabel.labelRes)
                            else whisperLanguage,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
                PanelDropdownMenu(
                        expanded = whisperLangExpanded,
                        onDismissRequest = { whisperLangExpanded = false },
                        modifier = Modifier.width(menuWidth)
                ) {
                    whisperTranscriptionLanguageOptions.forEach { opt ->
                        DropdownMenuItem(
                                text = {
                                    Text(
                                            stringResource(opt.labelRes),
                                            style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = {
                                    onWhisperLanguageChange(opt.whisperCode)
                                    whisperLangExpanded = false
                                },
                        )
                    }
                }
            }
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
                        modifier = Modifier.weight(1f),
                ) {
                    Text(
                            stringResource(Res.string.label_vad_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = null,
                            modifier =
                                    Modifier.size(18.dp).clip(CircleShape).clickable {
                                        showVadHelp = true
                                    },
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    )
                }
                Switch(
                        checked = whisperVadEnabled,
                        onCheckedChange = onWhisperVadChange,
                        modifier = Modifier.scale(0.75f),
                        colors =
                                SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        checkedBorderColor = Color.Transparent,
                                )
                )
            }
        }
    }
    if (showVadHelp) {
        AlertDialog(
                onDismissRequest = { showVadHelp = false },
                title = { Text(stringResource(Res.string.section_vad)) },
                text = { Text(stringResource(Res.string.dialog_vad_desc)) },
                confirmButton = {
                    TextButton(onClick = { showVadHelp = false }) {
                        Text(stringResource(Res.string.action_close))
                    }
                },
        )
    }
}

@Composable
private fun ModelGroupHeader(text: String) {
    val spacing = AppTheme.spacing
    Row(
            Modifier.fillMaxWidth().height(28.dp).padding(horizontal = spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TranslationSettingCard(
        tooling: ToolingSettings,
        onUpdateTooling: ((ToolingSettings) -> ToolingSettings) -> Unit,
        onTranslationEngineChanged: (TranslationEngine) -> Unit = {},
        modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    var targetLangExpanded by remember { mutableStateOf(false) }
    var engineExpanded by remember { mutableStateOf(false) }
    val optionTextStyle = MaterialTheme.typography.bodyMedium

    val density = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }

    Card(
            colors = panelCardColors,
            elevation = CardDefaults.cardElevation(spacing.xSmall),
            modifier = modifier.fillMaxWidth(),
    ) {
        Column(
                Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                    stringResource(Res.string.label_target_language),
                    style = MaterialTheme.typography.labelMedium
            )
            Box(
                    Modifier.fillMaxWidth().onGloballyPositioned {
                        menuWidth = with(density) { it.size.width.toDp() }
                    }
            ) {
                OutlinedButton(onClick = { targetLangExpanded = true }, Modifier.fillMaxWidth()) {
                    val label =
                            targetLanguageOptions.firstOrNull { it.id == tooling.targetLanguage }
                    Text(
                            if (label != null) stringResource(label.labelRes)
                            else tooling.targetLanguage,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = optionTextStyle
                    )
                }
                PanelDropdownMenu(
                        expanded = targetLangExpanded,
                        onDismissRequest = { targetLangExpanded = false },
                        modifier = Modifier.width(menuWidth)
                ) {
                    targetLanguageOptions.forEach { opt ->
                        DropdownMenuItem(
                                text = {
                                    Text(stringResource(opt.labelRes), style = optionTextStyle)
                                },
                                onClick = {
                                    onUpdateTooling { it.copy(targetLanguage = opt.id) }
                                    targetLangExpanded = false
                                },
                        )
                    }
                }
            }

            Text(
                    stringResource(Res.string.label_translation_engine),
                    style = MaterialTheme.typography.labelMedium
            )
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { engineExpanded = true }, Modifier.fillMaxWidth()) {
                    Text(
                            stringResource(tooling.translationEngine.labelRes),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = optionTextStyle
                    )
                }
                PanelDropdownMenu(
                        expanded = engineExpanded,
                        onDismissRequest = { engineExpanded = false },
                        modifier = Modifier.width(menuWidth)
                ) {
                    translationEngineMenuOrder
                            .filter {
                                it != TranslationEngine.APPLE ||
                                        com.danteandroid.transbee.utils.OsUtils.isMacOs()
                            }
                            .forEach { eng ->
                                DropdownMenuItem(
                                        text = { Text(stringResource(eng.labelRes), style = optionTextStyle) },
                                        onClick = {
                                            onUpdateTooling { it.copy(translationEngine = eng) }
                                            engineExpanded = false
                                            onTranslationEngineChanged(eng)
                                        },
                                )
                            }
                }
            }
        }
    }
}

@Composable
fun ExportSettingCard(
        tooling: ToolingSettings,
        onUpdateTooling: ((ToolingSettings) -> ToolingSettings) -> Unit,
        modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    var formatExpanded by remember { mutableStateOf(false) }
    var markdownFormatExpanded by remember { mutableStateOf(false) }
    var showMarkdownHelp by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }

    Card(
            colors = panelCardColors,
            elevation = CardDefaults.cardElevation(spacing.xSmall),
            modifier = modifier.fillMaxWidth(),
    ) {
        Column(
                Modifier.padding(spacing.medium).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                    stringResource(Res.string.label_subtitle_format),
                    style = MaterialTheme.typography.labelMedium
            )
            Box(
                    Modifier.fillMaxWidth().onGloballyPositioned {
                        menuWidth = with(density) { it.size.width.toDp() }
                    }
            ) {
                val currentFormat = ExportFormat.fromId(tooling.exportFormat)
                OutlinedButton(onClick = { formatExpanded = true }, Modifier.fillMaxWidth()) {
                    Text(
                            stringResource(currentFormat.labelRes),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
                PanelDropdownMenu(
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false },
                        modifier = Modifier.width(menuWidth)
                ) {
                    ExportFormat.entries.forEach { fmt ->
                        DropdownMenuItem(
                                text = { Text(stringResource(fmt.labelRes), style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    onUpdateTooling { it.copy(exportFormat = fmt.id) }
                                    formatExpanded = false
                                },
                        )
                    }
                }
            }

            val selected = tooling.subtitleOutputs.toSet()
            Text(
                    stringResource(Res.string.label_subtitle_content),
                    style = MaterialTheme.typography.labelMedium
            )
            Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp).height(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                SubtitleOutputKind.entries.forEach { item ->
                    val isSelected = item.id in selected
                    Surface(
                            onClick = {
                                onUpdateTooling { s ->
                                    val current = s.subtitleOutputs.toMutableSet()
                                    if (isSelected) current.remove(item.id)
                                    else current.add(item.id)
                                    s.copy(
                                            subtitleOutputs =
                                                    SubtitleOutputKind.entries
                                                            .map { it.id }
                                                            .filter { it in current },
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border =
                                    BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            else
                                                    MaterialTheme.colorScheme.outline.copy(
                                                            alpha = 0.2f
                                                    )
                                    ),
                            color =
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                    stringResource(item.labelRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                            if (isSelected)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().padding(vertical = spacing.medium).height(1.dp)) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = androidx.compose.ui.geometry.Offset(0f, 0.5f),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0.5f),
                            pathEffect = pathEffect,
                            strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xSmall)
            ) {
                Text(
                        stringResource(Res.string.label_markdown_format),
                        style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        modifier =
                                Modifier.size(16.dp).clip(CircleShape).clickable {
                                    showMarkdownHelp = true
                                },
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                        onClick = { markdownFormatExpanded = true },
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                            stringResource(tooling.pdfTranslateFormat.labelRes),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
                PanelDropdownMenu(
                        expanded = markdownFormatExpanded,
                        onDismissRequest = { markdownFormatExpanded = false },
                        modifier = Modifier.width(menuWidth),
                ) {
                    PdfTranslateFormat.entries.forEach { fmt ->
                        DropdownMenuItem(
                                text = { Text(stringResource(fmt.labelRes), style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    onUpdateTooling { it.copy(pdfTranslateFormat = fmt) }
                                    markdownFormatExpanded = false
                                },
                        )
                    }
                }
            }
        }
    }

    if (showMarkdownHelp) {
        MarkdownHelpDialog(onDismiss = { showMarkdownHelp = false })
    }
}

@Composable
private fun MarkdownHelpDialog(onDismiss: () -> Unit) {
    val helpContent = stringResource(Res.string.markdown_format_help_content)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.label_markdown_format)) },
        text = {
            Text(helpContent)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_confirm))
            }
        }
    )
}

@Composable
private fun WhisperModelMenuItems(
        models: List<WhisperModelOption>,
        menuTextStyle: TextStyle,
        modelDownload: ModelDownloadUiState,
        showSize: Boolean = true,
        onSelect: (WhisperModelOption) -> Unit,
        onDownload: (WhisperModelOption) -> Unit,
        onStopDownload: () -> Unit,
) {
    models.forEach { opt ->
        val parts = opt.label.split("（", "）")
        val name = parts.getOrNull(0) ?: opt.label
        val size = parts.getOrNull(1)?.let { " $it" } ?: ""
        val displaySize = if (showSize) size else ""

        if (opt.isDownloaded()) {
            DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(name, style = menuTextStyle)
                            if (displaySize.isNotEmpty()) {
                                Text(
                                        displaySize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 4.dp, bottom = 1.dp)
                                )
                            }
                        }
                    },
                    onClick = { onSelect(opt) },
            )
        } else {
            val downloadingThis = modelDownload.active && modelDownload.fileName == opt.fileName
            Row(
                    Modifier.fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                            name,
                            style = menuTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (displaySize.isNotEmpty()) {
                        Text(
                                displaySize,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, bottom = 1.dp)
                        )
                    }
                }
                TextButton(
                        onClick = { if (downloadingThis) onStopDownload() else onDownload(opt) }
                ) {
                    Text(
                            stringResource(
                                    if (downloadingThis) Res.string.action_stop_download
                                    else Res.string.action_download
                            ),
                            style = menuTextStyle,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ModelSettingCardPreview() {
    TransbeeTheme {
        val presets = WhisperModelCatalog.presets
        Column(Modifier.padding(16.dp)) {
            ModelSettingCard(
                    selectedPreset = presets.firstOrNull { it.id == "base" } ?: presets.first(),
                    modelDownload = ModelDownloadUiState(),
                    whisperLanguage = "auto",
                    onWhisperLanguageChange = {},
                    whisperVadEnabled = true,
                    onWhisperVadChange = {},
                    onSelectModel = {},
                    onDownloadModel = {},
                    onStopDownload = {},
            )
        }
    }
}

@Preview
@Composable
private fun TranslationSettingCardPreview() {
    TransbeeTheme {
        Column(Modifier.padding(16.dp)) {
            TranslationSettingCard(tooling = ToolingSettings(), onUpdateTooling = {})
        }
    }
}

@Preview
@Composable
private fun ExportSettingCardPreview() {
    TransbeeTheme {
        Column(Modifier.padding(16.dp)) {
            ExportSettingCard(tooling = ToolingSettings(), onUpdateTooling = {})
        }
    }
}
