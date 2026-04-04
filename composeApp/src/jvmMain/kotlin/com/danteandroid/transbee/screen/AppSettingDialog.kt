@file:OptIn(ExperimentalMaterial3Api::class)

package com.danteandroid.transbee.screen

import androidx.compose.foundation.clickable
import com.danteandroid.transbee.ui.AppVerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.danteandroid.transbee.AppTheme
import com.danteandroid.transbee.settings.ToolingSettings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import transbee.composeapp.generated.resources.Res
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
import transbee.composeapp.generated.resources.dialog_google_help_url
import transbee.composeapp.generated.resources.dialog_mineru_help_body
import transbee.composeapp.generated.resources.dialog_mineru_help_link
import transbee.composeapp.generated.resources.dialog_mineru_help_title
import transbee.composeapp.generated.resources.dialog_mineru_help_url
import transbee.composeapp.generated.resources.engine_apple
import transbee.composeapp.generated.resources.engine_deepl
import transbee.composeapp.generated.resources.engine_google
import transbee.composeapp.generated.resources.engine_openai
import transbee.composeapp.generated.resources.label_deepl_key
import transbee.composeapp.generated.resources.label_google_api_key
import transbee.composeapp.generated.resources.label_mineru_token
import transbee.composeapp.generated.resources.label_openai_base_url
import transbee.composeapp.generated.resources.label_openai_key
import transbee.composeapp.generated.resources.label_openai_model
import transbee.composeapp.generated.resources.locale_switch_to_en
import transbee.composeapp.generated.resources.locale_switch_to_zh
import transbee.composeapp.generated.resources.section_mineru
import transbee.composeapp.generated.resources.section_transcription_cache
import transbee.composeapp.generated.resources.section_translation

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
    var showCustomLlmHelp by remember { mutableStateOf(false) }
    var showAppleHelp by remember { mutableStateOf(false) }
    var showGoogleHelp by remember { mutableStateOf(false) }
    var showDeepLHelp by remember { mutableStateOf(false) }
    var showMinerUHelp by remember { mutableStateOf(false) }

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
            Box(Modifier.heightIn(max = 500.dp)) {
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

                    // Google Translate
                    EngineHeader(stringResource(Res.string.engine_google)) {
                        showGoogleHelp = true
                    }
                    ToolingTextField(
                        value = tooling.googleApiKey,
                        onValueChange = { newValue -> onUpdateTooling { s -> s.copy(googleApiKey = newValue) } },
                        labelRes = Res.string.label_google_api_key,
                    )

                    // DeepL
                    EngineHeader(stringResource(Res.string.engine_deepl)) {
                        showDeepLHelp = true
                    }
                    ToolingTextField(
                        value = tooling.deeplApiKey,
                        onValueChange = { newValue -> onUpdateTooling { s -> s.copy(deeplApiKey = newValue) } },
                        labelRes = Res.string.label_deepl_key,
                    )

                    // OpenAI / Custom LLM
                    EngineHeader(stringResource(Res.string.engine_openai)) {
                        showCustomLlmHelp = true
                    }
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

                    // Apple Translate
                    EngineHeader(stringResource(Res.string.engine_apple)) {
                        showAppleHelp = true
                    }

                    HorizontalDivider()
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearTranscriptionCache) {
                        Text(
                            stringResource(Res.string.action_clear_transcription_cache),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                com.danteandroid.transbee.ui.AppVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
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
