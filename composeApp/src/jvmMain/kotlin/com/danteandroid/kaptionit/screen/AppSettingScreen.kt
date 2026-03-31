package com.danteandroid.kaptionit.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danteandroid.kaptionit.AppTheme
import com.danteandroid.kaptionit.KaptionItTheme
import com.danteandroid.kaptionit.settings.ToolingSettings
import com.danteandroid.kaptionit.translate.TranslationEngine
import com.danteandroid.kaptionit.ui.ExportFormat
import com.danteandroid.kaptionit.ui.ModelDownloadUiState
import com.danteandroid.kaptionit.ui.SubtitleOutputKind
import com.danteandroid.kaptionit.ui.labelRes
import com.danteandroid.kaptionit.ui.targetLanguageOptions
import com.danteandroid.kaptionit.ui.whisperTranscriptionLanguageOptions
import com.danteandroid.kaptionit.whisper.WhisperModelCatalog
import com.danteandroid.kaptionit.whisper.WhisperModelOption
import com.danteandroid.kaptionit.whisper.isDownloaded
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.action_close
import kaptionit.composeapp.generated.resources.action_download
import kaptionit.composeapp.generated.resources.action_stop_download
import kaptionit.composeapp.generated.resources.dialog_apple_help_body
import kaptionit.composeapp.generated.resources.dialog_apple_help_link
import kaptionit.composeapp.generated.resources.dialog_apple_help_title
import kaptionit.composeapp.generated.resources.dialog_apple_help_url
import kaptionit.composeapp.generated.resources.dialog_custom_llm_help_body
import kaptionit.composeapp.generated.resources.dialog_custom_llm_help_title
import kaptionit.composeapp.generated.resources.dialog_vad_desc
import kaptionit.composeapp.generated.resources.label_deepl_key
import kaptionit.composeapp.generated.resources.label_google_api_key
import kaptionit.composeapp.generated.resources.label_openai_base_url
import kaptionit.composeapp.generated.resources.label_openai_key
import kaptionit.composeapp.generated.resources.label_openai_model
import kaptionit.composeapp.generated.resources.label_recognition_model
import kaptionit.composeapp.generated.resources.label_subtitle_content
import kaptionit.composeapp.generated.resources.label_subtitle_format
import kaptionit.composeapp.generated.resources.label_target_language
import kaptionit.composeapp.generated.resources.label_translation_engine
import kaptionit.composeapp.generated.resources.label_vad_toggle
import kaptionit.composeapp.generated.resources.label_whisper_language
import kaptionit.composeapp.generated.resources.model_group_english_only
import kaptionit.composeapp.generated.resources.model_group_general
import kaptionit.composeapp.generated.resources.section_export
import kaptionit.composeapp.generated.resources.section_model_settings
import kaptionit.composeapp.generated.resources.section_translation
import kaptionit.composeapp.generated.resources.section_vad
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val panelCardColors
    @Composable get() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

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
    val menuTextStyle = MaterialTheme.typography.bodySmall

    SectionTitleWithIcon(Icons.Filled.Psychology, stringResource(Res.string.section_model_settings))
    Card(
        colors = panelCardColors,
        elevation = CardDefaults.cardElevation(spacing.xSmall),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(spacing.medium).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(stringResource(Res.string.label_recognition_model), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { menuExpanded = true }, Modifier.fillMaxWidth()) {
                    Text(selectedPreset.label)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                        ModelGroupHeader(stringResource(Res.string.model_group_general))
                        WhisperModelMenuItems(
                            models = WhisperModelCatalog.presetsMain,
                            menuTextStyle = menuTextStyle,
                            modelDownload = modelDownload,
                            onSelect = { menuExpanded = false; onSelectModel(it) },
                            onDownload = { menuExpanded = false; onDownloadModel(it) },
                            onStopDownload = onStopDownload,
                        )
                        HorizontalDivider()
                        Spacer(Modifier.size(spacing.large))
                        ModelGroupHeader(stringResource(Res.string.model_group_english_only))
                        WhisperModelMenuItems(
                            models = WhisperModelCatalog.presetsEnglishOnly,
                            menuTextStyle = menuTextStyle,
                            modelDownload = modelDownload,
                            onSelect = { menuExpanded = false; onSelectModel(it) },
                            onDownload = { menuExpanded = false; onDownloadModel(it) },
                            onStopDownload = onStopDownload,
                        )
                    }
                }
            }
            Text(stringResource(Res.string.label_whisper_language), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { whisperLangExpanded = true }, Modifier.fillMaxWidth()) {
                    val code = whisperLanguage.trim().lowercase().ifEmpty { "auto" }
                    val langLabel = whisperTranscriptionLanguageOptions.firstOrNull { it.whisperCode == code }
                    Text(
                        if (langLabel != null) stringResource(langLabel.labelRes) else whisperLanguage,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                DropdownMenu(expanded = whisperLangExpanded, onDismissRequest = { whisperLangExpanded = false }) {
                    whisperTranscriptionLanguageOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(opt.labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
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
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { showVadHelp = true },
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    )
                }
                Switch(
                    checked = whisperVadEnabled,
                    onCheckedChange = onWhisperVadChange,
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
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TranslationSettingCard(
    tooling: ToolingSettings,
    onUpdateTooling: ((ToolingSettings) -> ToolingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    var targetLangExpanded by remember { mutableStateOf(false) }
    var engineExpanded by remember { mutableStateOf(false) }
    var showCustomLlmHelp by remember { mutableStateOf(false) }
    var showAppleHelp by remember { mutableStateOf(false) }
    val optionTextStyle = MaterialTheme.typography.bodyLarge

    SectionTitleWithIcon(Icons.Filled.Translate, stringResource(Res.string.section_translation))
    Card(
        colors = panelCardColors,
        elevation = CardDefaults.cardElevation(spacing.xSmall),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(stringResource(Res.string.label_target_language), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { targetLangExpanded = true }, Modifier.fillMaxWidth()) {
                    val label = targetLanguageOptions.firstOrNull { it.id == tooling.targetLanguage }
                    Text(
                        if (label != null) stringResource(label.labelRes) else tooling.targetLanguage,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                DropdownMenu(expanded = targetLangExpanded, onDismissRequest = { targetLangExpanded = false }) {
                    targetLanguageOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(stringResource(opt.labelRes), style = optionTextStyle) },
                            onClick = {
                                onUpdateTooling { it.copy(targetLanguage = opt.id) }
                                targetLangExpanded = false
                            },
                        )
                    }
                }
            }

            Text(stringResource(Res.string.label_translation_engine), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { engineExpanded = true }, Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(tooling.translationEngine.labelRes),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
                DropdownMenu(expanded = engineExpanded, onDismissRequest = { engineExpanded = false }) {
                    TranslationEngine.entries.filter { it != TranslationEngine.APPLE || com.danteandroid.kaptionit.utils.OsUtils.isMacOs() }
                        .forEach { eng ->
                        DropdownMenuItem(
                            text = { Text(stringResource(eng.labelRes)) },
                            onClick = {
                                onUpdateTooling { it.copy(translationEngine = eng) }
                                engineExpanded = false
                            },
                            trailingIcon = {
                                if (eng == TranslationEngine.OPENAI || eng == TranslationEngine.APPLE) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.HelpOutline,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (eng == TranslationEngine.OPENAI) showCustomLlmHelp = true
                                                else showAppleHelp = true
                                                engineExpanded = false
                                            },
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            EngineSpecificFields(tooling, onUpdateTooling)
        }
    }

    if (showCustomLlmHelp) {
        CustomLlmHelpDialog(onDismiss = { showCustomLlmHelp = false })
    }
    if (showAppleHelp) {
        AppleHelpDialog(onDismiss = { showAppleHelp = false })
    }
}

@Composable
private fun EngineSpecificFields(
    tooling: ToolingSettings,
    onUpdateTooling: ((ToolingSettings) -> ToolingSettings) -> Unit,
) {
    when (tooling.translationEngine) {
        TranslationEngine.APPLE -> Unit
        TranslationEngine.GOOGLE -> {
            ToolingTextField(
                value = tooling.googleApiKey,
                onValueChange = { onUpdateTooling { s -> s.copy(googleApiKey = it) } },
                labelRes = Res.string.label_google_api_key,
            )
        }
        TranslationEngine.DEEPL -> {
            ToolingTextField(
                value = tooling.deeplApiKey,
                onValueChange = { onUpdateTooling { s -> s.copy(deeplApiKey = it) } },
                labelRes = Res.string.label_deepl_key,
            )
        }
        TranslationEngine.OPENAI -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolingTextField(
                    value = tooling.openAiKey,
                    onValueChange = { onUpdateTooling { s -> s.copy(openAiKey = it) } },
                    labelRes = Res.string.label_openai_key,
                )
                ToolingTextField(
                    value = tooling.openAiBaseUrl,
                    onValueChange = { onUpdateTooling { s -> s.copy(openAiBaseUrl = it) } },
                    labelRes = Res.string.label_openai_base_url,
                )
                ToolingTextField(
                    value = tooling.openAiModel,
                    onValueChange = { onUpdateTooling { s -> s.copy(openAiModel = it.lowercase()) } },
                    labelRes = Res.string.label_openai_model,
                )
            }
        }
    }
}

@Composable
private fun CustomLlmHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_custom_llm_help_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.dialog_custom_llm_help_body), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}

@Composable
private fun AppleHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_apple_help_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                AppleTranslateHelpText()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}

@Composable
private fun AppleTranslateHelpText() {
    val body = stringResource(Res.string.dialog_apple_help_body)
    val linkText = stringResource(Res.string.dialog_apple_help_link)
    val url = stringResource(Res.string.dialog_apple_help_url)
    val linkColor = MaterialTheme.colorScheme.primary
    val typography = MaterialTheme.typography.bodyMedium

    val annotated = remember(body, linkText, url, linkColor) {
        buildAnnotatedString {
            append(body)
            append("\n\n")
            withLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) {
                append(linkText)
            }
        }
    }
    Text(
        annotated,
        style = typography.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToolingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: StringResource,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun ExportSettingCard(
    tooling: ToolingSettings,
    onUpdateTooling: ((ToolingSettings) -> ToolingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    var formatExpanded by remember { mutableStateOf(false) }

    SectionTitleWithIcon(Icons.Filled.Subtitles, stringResource(Res.string.section_export))
    Card(
        colors = panelCardColors,
        elevation = CardDefaults.cardElevation(spacing.xSmall),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(spacing.medium).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(stringResource(Res.string.label_subtitle_format), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth()) {
                val currentFormat = ExportFormat.fromId(tooling.exportFormat)
                OutlinedButton(onClick = { formatExpanded = true }, Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(currentFormat.labelRes),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                    ExportFormat.entries.forEach { fmt ->
                        DropdownMenuItem(
                            text = { Text(stringResource(fmt.labelRes)) },
                            onClick = {
                                onUpdateTooling { it.copy(exportFormat = fmt.id) }
                                formatExpanded = false
                            },
                        )
                    }
                }
            }

            Text(stringResource(Res.string.label_subtitle_content), style = MaterialTheme.typography.labelMedium)
            val selected = tooling.subtitleOutputs.toSet()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SubtitleOutputKind.entries.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.id in selected,
                            onCheckedChange = { checked ->
                                onUpdateTooling { s ->
                                    val current = s.subtitleOutputs.toMutableSet()
                                    if (checked) current.add(item.id) else current.remove(item.id)
                                    s.copy(
                                        subtitleOutputs = SubtitleOutputKind.entries
                                            .map { it.id }
                                            .filter { it in current },
                                    )
                                }
                            },
                            modifier = Modifier.size(30.dp),
                        )
                        Text(
                            stringResource(item.labelRes),
                            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 14.sp, stepSize = 2.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhisperModelMenuItems(
    models: List<WhisperModelOption>,
    menuTextStyle: TextStyle,
    modelDownload: ModelDownloadUiState,
    onSelect: (WhisperModelOption) -> Unit,
    onDownload: (WhisperModelOption) -> Unit,
    onStopDownload: () -> Unit,
) {
    models.forEach { opt ->
        if (opt.isDownloaded()) {
            DropdownMenuItem(
                text = { Text(opt.label, style = menuTextStyle) },
                onClick = { onSelect(opt) },
            )
        } else {
            val downloadingThis = modelDownload.active && modelDownload.fileName == opt.fileName
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(opt.label, style = menuTextStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (downloadingThis) onStopDownload() else onDownload(opt) }) {
                    Text(
                        stringResource(if (downloadingThis) Res.string.action_stop_download else Res.string.action_download),
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
    KaptionItTheme {
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
    KaptionItTheme {
        Column(Modifier.padding(16.dp)) {
            TranslationSettingCard(tooling = ToolingSettings(), onUpdateTooling = {})
        }
    }
}

@Preview
@Composable
private fun ExportSettingCardPreview() {
    KaptionItTheme {
        Column(Modifier.padding(16.dp)) {
            ExportSettingCard(tooling = ToolingSettings(), onUpdateTooling = {})
        }
    }
}
