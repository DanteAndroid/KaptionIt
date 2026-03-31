@file:OptIn(ExperimentalMaterial3Api::class)

package com.danteandroid.kaptionit.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danteandroid.kaptionit.AppTheme
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.action_clear_transcription_cache
import kaptionit.composeapp.generated.resources.action_confirm
import kaptionit.composeapp.generated.resources.desc_transcription_cache
import kaptionit.composeapp.generated.resources.dialog_about_body
import kaptionit.composeapp.generated.resources.dialog_about_title
import kaptionit.composeapp.generated.resources.label_ui_language
import kaptionit.composeapp.generated.resources.locale_switch_to_en
import kaptionit.composeapp.generated.resources.locale_switch_to_zh
import kaptionit.composeapp.generated.resources.section_transcription_cache
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppSettingDialog(
    onDismissRequest: () -> Unit,
    onLocaleZh: () -> Unit,
    onLocaleEn: () -> Unit,
    onClearTranscriptionCache: () -> Unit,
) {
    val spacing = AppTheme.spacing
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        title = { Text(stringResource(Res.string.dialog_about_title)) },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                Text(
                    stringResource(
                        Res.string.dialog_about_body,
                        com.danteandroid.kaptionit.bundled.BuildConfig.APP_VERSION
                    )
                )
                HorizontalDivider()
                Spacer(Modifier.height(spacing.small))
                Text(
                    stringResource(Res.string.label_ui_language),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    TextButton(onClick = onLocaleZh) {
                        Text(stringResource(Res.string.locale_switch_to_zh))
                    }
                    TextButton(onClick = onLocaleEn) {
                        Text(stringResource(Res.string.locale_switch_to_en))
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(spacing.small))
                Text(
                    stringResource(Res.string.section_transcription_cache),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(Res.string.desc_transcription_cache),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onClearTranscriptionCache) {
                    Text(stringResource(Res.string.action_clear_transcription_cache))
                }
            }
        },
    )
}
