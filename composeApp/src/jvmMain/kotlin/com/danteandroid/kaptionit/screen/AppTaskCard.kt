package com.danteandroid.transbee.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.danteandroid.transbee.AppTheme
import com.danteandroid.transbee.process.PipelineEngine
import com.danteandroid.transbee.process.PipelinePhase
import com.danteandroid.transbee.process.isActivelyProcessing
import com.danteandroid.transbee.ui.TaskRecord
import com.danteandroid.transbee.ui.TranslationTaskStats
import com.danteandroid.transbee.ui.label
import com.danteandroid.transbee.utils.OsUtils
import org.jetbrains.compose.resources.stringResource
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.action_delete
import transbee.composeapp.generated.resources.action_open_folder
import transbee.composeapp.generated.resources.action_open_video
import transbee.composeapp.generated.resources.action_retry_recognize
import transbee.composeapp.generated.resources.duration_format_min_sec
import transbee.composeapp.generated.resources.duration_format_sec_only
import transbee.composeapp.generated.resources.duration_zero
import transbee.composeapp.generated.resources.task_done_detail_full
import transbee.composeapp.generated.resources.task_done_detail_skipped
import java.io.File

@Composable
fun TaskRowCard(
    task: TaskRecord,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val running = task.phase.isActivelyProcessing()
    val (chipBg, chipFg) = phaseChipColors(task.phase)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(spacing.xSmall),
    ) {
        Column(
            Modifier.padding(
                horizontal = spacing.large,
                vertical = spacing.medium,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "\uD83D\uDCC4 ${task.fileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    task.phase.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = chipFg,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(chipBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (running) {
                if (task.progressIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { task.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
            if (task.phase != PipelinePhase.Queued) {
                TaskDetailBlock(task = task, running = running)
            }

            ActionRow(task, running, onDelete, onRetry)
        }
    }
}

@Composable
private fun ActionRow(
    task: TaskRecord,
    running: Boolean,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = AppTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (task.phase == PipelinePhase.Done && task.outputPath != null) {
            TextButton(onClick = {
                OsUtils.revealInFileBrowser(File(task.outputPath))
            }) {
                Text(stringResource(Res.string.action_open_folder))
            }
        }
        val srcPath = task.sourcePath
        if (task.phase == PipelinePhase.Done && !srcPath.isNullOrBlank()) {
            val sourceFile = File(srcPath)
            val openPath =
                if (PipelineEngine.isDocumentSourceFile(sourceFile)) task.outputPath else srcPath
            if (!openPath.isNullOrBlank()) {
                TextButton(onClick = { runCatching { OsUtils.openFile(File(openPath)) } }) {
                    Text(stringResource(Res.string.action_open_video))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(Res.string.action_delete),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            )
        }
        if (!running && task.phase != PipelinePhase.Queued) {
            IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.action_retry_recognize),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun TaskDetailBlock(task: TaskRecord, running: Boolean) {
    val text = when {
        running -> task.message
        !task.error.isNullOrBlank() -> task.error.orEmpty()
        task.phase == PipelinePhase.Done -> {
            val stats = task.translationStats
            if (stats != null) translationStatsLine(stats) else task.message
        }
        else -> task.message
    }
    val color = when {
        !task.error.isNullOrBlank() -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val annotatedStr = buildAnnotatedString {
        val regex = "https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]*".toRegex()
        var lastIndex = 0
        regex.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))
            val url = matchResult.value
            val linkStyle = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
            withLink(LinkAnnotation.Url(url, styles = linkStyle)) {
                append(url)
            }
            lastIndex = matchResult.range.last + 1
        }
        append(text.substring(lastIndex))
    }

    SelectionContainer {
        Text(
            text = annotatedStr,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            minLines = 1,
            maxLines = if (running) 2 else 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = if (running) 0.dp else 8.dp),
        )
    }
}

@Composable
private fun translationStatsLine(stats: TranslationTaskStats): String {
    val rec = formatDurationMsForDone(stats.recognitionDurationMs)
    return if (stats.skipped) {
        stringResource(Res.string.task_done_detail_skipped, rec, stats.lineCount)
    } else {
        val trans = formatDurationMsForDone(stats.translationDurationMs)
        stringResource(Res.string.task_done_detail_full, rec, trans, stats.lineCount)
    }
}

@Composable
private fun formatDurationMsForDone(ms: Long): String {
    if (ms <= 0L) return stringResource(Res.string.duration_zero)
    val totalSec = ((ms + 500) / 1000).coerceAtLeast(1)
    val m = (totalSec / 60).toInt()
    val s = (totalSec % 60).toInt()
    return if (m > 0) {
        stringResource(Res.string.duration_format_min_sec, m, s)
    } else {
        stringResource(Res.string.duration_format_sec_only, s)
    }
}

@Composable
private fun phaseChipColors(phase: PipelinePhase): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (phase) {
        PipelinePhase.Done -> cs.secondaryContainer to cs.onSecondaryContainer
        PipelinePhase.Failed -> cs.errorContainer to cs.onErrorContainer
        PipelinePhase.Queued, PipelinePhase.Cancelled -> cs.surfaceVariant to cs.onSurfaceVariant
        else -> cs.primaryContainer to cs.onPrimaryContainer
    }
}
