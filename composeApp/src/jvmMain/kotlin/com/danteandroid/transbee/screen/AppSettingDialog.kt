@file:OptIn(ExperimentalMaterial3Api::class)

package com.danteandroid.transbee.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import com.danteandroid.transbee.ui.AppVerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.danteandroid.transbee.AppTheme
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.scale
import com.danteandroid.transbee.settings.ForcedTranslationTerm
import transbee.composeapp.generated.resources.label_enable
import transbee.composeapp.generated.resources.label_enable_transcription_cache
import com.danteandroid.transbee.settings.ToolingSettings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.action_clear
import transbee.composeapp.generated.resources.action_add_term
import transbee.composeapp.generated.resources.action_clear_transcription_cache
import transbee.composeapp.generated.resources.action_close
import transbee.composeapp.generated.resources.action_confirm
import transbee.composeapp.generated.resources.desc_transcription_cache
import transbee.composeapp.generated.resources.dialog_about_body
import transbee.composeapp.generated.resources.dialog_about_title
import transbee.composeapp.generated.resources.dialog_apple_help_body
import transbee.composeapp.generated.resources.dialog_apple_help_link
import transbee.composeapp.generated.resources.dialog_apple_help_title
import transbee.composeapp.generated.resources.dialog_apple_help_url
import transbee.composeapp.generated.resources.dialog_custom_llm_body
import transbee.composeapp.generated.resources.dialog_custom_llm_title
import transbee.composeapp.generated.resources.dialog_deepl_help_body
import transbee.composeapp.generated.resources.dialog_deepl_help_link
import transbee.composeapp.generated.resources.dialog_deepl_help_title
import transbee.composeapp.generated.resources.dialog_deepl_help_url
import transbee.composeapp.generated.resources.dialog_google_help_body
import transbee.composeapp.generated.resources.dialog_google_help_link
import transbee.composeapp.generated.resources.dialog_google_help_title
import transbee.composeapp.generated.resources.dialog_gemini_help_body
import transbee.composeapp.generated.resources.dialog_gemini_help_link
import transbee.composeapp.generated.resources.dialog_gemini_help_title
import transbee.composeapp.generated.resources.dialog_gemini_help_url
import transbee.composeapp.generated.resources.dialog_google_help_url
import transbee.composeapp.generated.resources.dialog_mineru_help_body
import transbee.composeapp.generated.resources.dialog_mineru_help_link
import transbee.composeapp.generated.resources.dialog_mineru_help_title
import transbee.composeapp.generated.resources.dialog_mineru_help_url
import transbee.composeapp.generated.resources.engine_apple
import transbee.composeapp.generated.resources.engine_deepl
import transbee.composeapp.generated.resources.engine_gemini
import transbee.composeapp.generated.resources.engine_google
import transbee.composeapp.generated.resources.engine_openai
import transbee.composeapp.generated.resources.label_deepl_key
import transbee.composeapp.generated.resources.hint_forced_translation_source
import transbee.composeapp.generated.resources.hint_forced_translation_target
import transbee.composeapp.generated.resources.label_forced_translation_terms
import transbee.composeapp.generated.resources.label_gemini_api_key
import transbee.composeapp.generated.resources.label_gemini_model
import transbee.composeapp.generated.resources.label_google_api_key
import transbee.composeapp.generated.resources.label_mineru_token
import transbee.composeapp.generated.resources.label_openai_base_url
import transbee.composeapp.generated.resources.label_openai_key
import transbee.composeapp.generated.resources.label_openai_model
import transbee.composeapp.generated.resources.label_translation_prompt
import transbee.composeapp.generated.resources.hint_translation_prompt
import transbee.composeapp.generated.resources.locale_switch_to_en
import transbee.composeapp.generated.resources.locale_switch_to_zh
import transbee.composeapp.generated.resources.section_document_recognition_service
import transbee.composeapp.generated.resources.section_mineru
import transbee.composeapp.generated.resources.section_translation_service
import transbee.composeapp.generated.resources.section_transcription_cache
import transbee.composeapp.generated.resources.section_translation
import transbee.composeapp.generated.resources.msg_transcription_cache_cleared
import kotlinx.coroutines.launch

@Composable
fun AppSettingDialog(
    tooling: ToolingSettings,
    onUpdateTooling: ((ToolingSettings) -> ToolingSettings) -> Unit,
    onDismissRequest: () -> Unit,
    onLocaleZh: () -> Unit,
    onLocaleEn: () -> Unit,
    onClearTranscriptionCache: () -> Unit,
) {
    val spacing = AppTheme.spacing
    val translationSvcContentAnim = tween<IntSize>(durationMillis = 260, easing = FastOutSlowInEasing)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCustomLlmHelp by remember { mutableStateOf(false) }
    var showAppleHelp by remember { mutableStateOf(false) }
    var showGoogleHelp by remember { mutableStateOf(false) }
    var showDeepLHelp by remember { mutableStateOf(false) }
    var showMinerUHelp by remember { mutableStateOf(false) }
    var showGeminiHelp by remember { mutableStateOf(false) }
    var expandGoogleKeys by remember { mutableStateOf(false) }
    var expandGeminiKeys by remember { mutableStateOf(false) }
    var expandDeeplKeys by remember { mutableStateOf(false) }
    var expandOpenAiKeys by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        title = { Text(stringResource(Res.string.dialog_about_title)) },
        text = {
            val scrollState = rememberScrollState()
            val cacheClearedMessage = stringResource(Res.string.msg_transcription_cache_cleared)
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.heightIn(max = 620.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = spacing.large, end = 24.dp), // Match sidebar gap
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(
                                Res.string.dialog_about_body,
                                com.danteandroid.transbee.bundled.BuildConfig.APP_VERSION
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            TextButton(onClick = onLocaleZh) {
                                Text(
                                    stringResource(Res.string.locale_switch_to_zh),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            TextButton(onClick = onLocaleEn) {
                                Text(
                                    stringResource(Res.string.locale_switch_to_en),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    Text(
                        stringResource(Res.string.section_translation),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    OutlinedTextField(
                        value = tooling.translationPrompt,
                        onValueChange = { onUpdateTooling { s -> s.copy(translationPrompt = it) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.label_translation_prompt), style = MaterialTheme.typography.labelSmall) },
                        placeholder = {
                            Text(
                                stringResource(Res.string.hint_translation_prompt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                    )

                    EngineHeader(stringResource(Res.string.label_forced_translation_terms))
                    var forcedSourceInput by remember { mutableStateOf("") }
                    var forcedTargetInput by remember { mutableStateOf("") }
                    val addForcedTerm = {
                        val source = forcedSourceInput.trim()
                        val target = forcedTargetInput.trim()
                        if (source.isNotEmpty() && target.isNotEmpty()) {
                            onUpdateTooling { s ->
                                val next = (s.forcedTranslationTerms + ForcedTranslationTerm(source = source, target = target))
                                    .mapNotNull { item ->
                                        val src = item.source.trim()
                                        val dst = item.target.trim()
                                        if (src.isEmpty() || dst.isEmpty()) null else ForcedTranslationTerm(src, dst)
                                    }
                                    .distinctBy { item -> item.source.lowercase() to item.target }
                                s.copy(forcedTranslationTerms = next)
                            }
                            forcedSourceInput = ""
                            forcedTargetInput = ""
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = forcedSourceInput,
                            onValueChange = { forcedSourceInput = it },
                            label = { Text(stringResource(Res.string.hint_forced_translation_source), style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                    addForcedTerm()
                                    true
                                } else {
                                    false
                                }
                            },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = forcedTargetInput,
                            onValueChange = { forcedTargetInput = it },
                            label = { Text(stringResource(Res.string.hint_forced_translation_target), style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                    addForcedTerm()
                                    true
                                } else {
                                    false
                                }
                            },
                            singleLine = true,
                        )
                        TextButton(onClick = addForcedTerm) {
                            Text(
                                stringResource(Res.string.action_add_term),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (tooling.forcedTranslationTerms.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            verticalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            tooling.forcedTranslationTerms.forEach { item ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    ) {
                                        Text(
                                            text = "${item.source} → ${item.target}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        IconButton(
                                            onClick = {
                                                onUpdateTooling { s ->
                                                    s.copy(forcedTranslationTerms = s.forcedTranslationTerms.filterNot { it.source == item.source && it.target == item.target })
                                                }
                                            },
                                            modifier = Modifier.size(22.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(Res.string.action_clear),
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text(
                        stringResource(Res.string.section_translation_service),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    EngineHeader(stringResource(Res.string.engine_apple)) {
                        showAppleHelp = true
                    }

                    Column(Modifier.fillMaxWidth()) {
                        ExpandableSettingHeader(
                            title = stringResource(Res.string.engine_google),
                            expanded = expandGoogleKeys,
                            onExpandedChange = { expandGoogleKeys = it },
                            onHelpClick = { showGoogleHelp = true },
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (expandGoogleKeys) spacing.small else 0.dp)
                                .clipToBounds()
                                .animateContentSize(animationSpec = translationSvcContentAnim),
                        ) {
                            if (expandGoogleKeys) {
                                ToolingTextField(
                                    value = tooling.googleApiKey,
                                    onValueChange = { newValue -> onUpdateTooling { s -> s.copy(googleApiKey = newValue) } },
                                    labelRes = Res.string.label_google_api_key,
                                )
                            }
                        }
                    }

                    Column(Modifier.fillMaxWidth()) {
                        ExpandableSettingHeader(
                            title = stringResource(Res.string.engine_gemini),
                            expanded = expandGeminiKeys,
                            onExpandedChange = { expandGeminiKeys = it },
                            onHelpClick = { showGeminiHelp = true },
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (expandGeminiKeys) spacing.small else 0.dp)
                                .clipToBounds()
                                .animateContentSize(animationSpec = translationSvcContentAnim),
                        ) {
                            if (expandGeminiKeys) {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                                    ToolingTextField(
                                        value = tooling.geminiApiKey,
                                        onValueChange = { newValue -> onUpdateTooling { s -> s.copy(geminiApiKey = newValue) } },
                                        labelRes = Res.string.label_gemini_api_key,
                                    )
                                    ToolingTextField(
                                        value = tooling.geminiModel,
                                        onValueChange = { newValue -> onUpdateTooling { s -> s.copy(geminiModel = newValue.trim()) } },
                                        labelRes = Res.string.label_gemini_model,
                                    )
                                }
                            }
                        }
                    }

                    Column(Modifier.fillMaxWidth()) {
                        ExpandableSettingHeader(
                            title = stringResource(Res.string.engine_deepl),
                            expanded = expandDeeplKeys,
                            onExpandedChange = { expandDeeplKeys = it },
                            onHelpClick = { showDeepLHelp = true },
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (expandDeeplKeys) spacing.small else 0.dp)
                                .clipToBounds()
                                .animateContentSize(animationSpec = translationSvcContentAnim),
                        ) {
                            if (expandDeeplKeys) {
                                ToolingTextField(
                                    value = tooling.deeplApiKey,
                                    onValueChange = { newValue -> onUpdateTooling { s -> s.copy(deeplApiKey = newValue) } },
                                    labelRes = Res.string.label_deepl_key,
                                )
                            }
                        }
                    }

                    Column(Modifier.fillMaxWidth()) {
                        ExpandableSettingHeader(
                            title = stringResource(Res.string.engine_openai),
                            expanded = expandOpenAiKeys,
                            onExpandedChange = { expandOpenAiKeys = it },
                            onHelpClick = { showCustomLlmHelp = true },
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (expandOpenAiKeys) spacing.small else 0.dp)
                                .clipToBounds()
                                .animateContentSize(animationSpec = translationSvcContentAnim),
                        ) {
                            if (expandOpenAiKeys) {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                                    ToolingTextField(
                                        value = tooling.openAiKey,
                                        onValueChange = { newValue -> onUpdateTooling { s -> s.copy(openAiKey = newValue) } },
                                        labelRes = Res.string.label_openai_key,
                                    )
                                    ToolingTextField(
                                        value = tooling.openAiBaseUrl,
                                        onValueChange = { newValue -> onUpdateTooling { s -> s.copy(openAiBaseUrl = newValue) } },
                                        labelRes = Res.string.label_openai_base_url,
                                    )
                                    ToolingTextField(
                                        value = tooling.openAiModel,
                                        onValueChange = { newValue -> onUpdateTooling { s -> s.copy(openAiModel = newValue.lowercase()) } },
                                        labelRes = Res.string.label_openai_model,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text(
                        stringResource(Res.string.section_document_recognition_service),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    EngineHeader(stringResource(Res.string.section_mineru)) {
                        showMinerUHelp = true
                    }
                    ToolingTextField(
                        value = tooling.minerUToken,
                        onValueChange = { newValue -> onUpdateTooling { s -> s.copy(minerUToken = newValue) } },
                        labelRes = Res.string.label_mineru_token,
                    )

                    HorizontalDivider()
                    Text(
                        stringResource(Res.string.section_transcription_cache),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(Res.string.desc_transcription_cache),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(Res.string.label_enable),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.size(spacing.small))
                            Switch(
                                checked = tooling.useTranscriptionCache,
                                onCheckedChange = { newValue ->
                                    onUpdateTooling { it.copy(useTranscriptionCache = newValue) }
                                },
                                modifier = Modifier.scale(0.7f)
                            )
                        }
                        TextButton(
                            onClick = {
                                onClearTranscriptionCache()
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = cacheClearedMessage,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                        ) {
                            Text(
                                stringResource(Res.string.action_clear_transcription_cache),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                com.danteandroid.transbee.ui.AppVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
    )

    if (showGoogleHelp) {
        LinkHelpDialog(
            title = stringResource(Res.string.dialog_google_help_title),
            body = stringResource(Res.string.dialog_google_help_body),
            linkText = stringResource(Res.string.dialog_google_help_link),
            url = stringResource(Res.string.dialog_google_help_url),
            onDismiss = { showGoogleHelp = false }
        )
    }
    if (showDeepLHelp) {
        LinkHelpDialog(
            title = stringResource(Res.string.dialog_deepl_help_title),
            body = stringResource(Res.string.dialog_deepl_help_body),
            linkText = stringResource(Res.string.dialog_deepl_help_link),
            url = stringResource(Res.string.dialog_deepl_help_url),
            onDismiss = { showDeepLHelp = false }
        )
    }
    if (showGeminiHelp) {
        LinkHelpDialog(
            title = stringResource(Res.string.dialog_gemini_help_title),
            body = stringResource(Res.string.dialog_gemini_help_body),
            linkText = stringResource(Res.string.dialog_gemini_help_link),
            url = stringResource(Res.string.dialog_gemini_help_url),
            onDismiss = { showGeminiHelp = false }
        )
    }
    if (showCustomLlmHelp) {
        AlertDialog(
            onDismissRequest = { showCustomLlmHelp = false },
            title = { Text(stringResource(Res.string.dialog_custom_llm_title)) },
            text = {
                SelectionContainer {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        val helpContent = stringResource(Res.string.dialog_custom_llm_body)
                        Text(
                            helpContent,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCustomLlmHelp = false
                }) { Text(stringResource(Res.string.action_close)) }
            },
        )
    }
    if (showAppleHelp) {
        LinkHelpDialog(
            title = stringResource(Res.string.dialog_apple_help_title),
            body = stringResource(Res.string.dialog_apple_help_body),
            linkText = stringResource(Res.string.dialog_apple_help_link),
            url = stringResource(Res.string.dialog_apple_help_url),
            onDismiss = { showAppleHelp = false }
        )
    }
    if (showMinerUHelp) {
        LinkHelpDialog(
            title = stringResource(Res.string.dialog_mineru_help_title),
            body = stringResource(Res.string.dialog_mineru_help_body),
            linkText = stringResource(Res.string.dialog_mineru_help_link),
            url = stringResource(Res.string.dialog_mineru_help_url),
            onDismiss = { showMinerUHelp = false }
        )
    }
}

@Composable
private fun LinkHelpDialog(
    title: String,
    body: String,
    linkText: String,
    url: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    LinkHelpText(body = body, linkText = linkText, url = url)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}

@Composable
private fun LinkHelpText(body: String, linkText: String, url: String) {
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
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        ),
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
private fun ExpandableSettingHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onHelpClick: (() -> Unit)? = null,
) {
    val spacing = AppTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onHelpClick != null) {
            Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = spacing.xSmall)
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onHelpClick() },
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Remove else Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun EngineHeader(
    title: String,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    onHelpClick: (() -> Unit)? = null
) {
    val spacing = AppTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
        modifier = Modifier.padding(top = spacing.small)
    ) {
        Text(title, style = style, color = MaterialTheme.colorScheme.onSurface)
        if (onHelpClick != null) {
            Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onHelpClick() },
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
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
