@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
)

package com.danteandroid.kaptionit.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danteandroid.kaptionit.AppTheme
import com.danteandroid.kaptionit.KaptionItTheme
import com.danteandroid.kaptionit.process.PipelinePhase
import com.danteandroid.kaptionit.process.isActivelyProcessing
import com.danteandroid.kaptionit.ui.ModelDownloadUiState
import com.danteandroid.kaptionit.ui.TaskRecord
import com.danteandroid.kaptionit.utils.fileFromDragDropPath
import com.danteandroid.kaptionit.utils.pickVideoFileWithChooser
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.action_cancel
import kaptionit.composeapp.generated.resources.action_choose_file
import kaptionit.composeapp.generated.resources.action_confirm
import kaptionit.composeapp.generated.resources.action_delete_all
import kaptionit.composeapp.generated.resources.action_pause_all
import kaptionit.composeapp.generated.resources.action_settings
import kaptionit.composeapp.generated.resources.action_start_all
import kaptionit.composeapp.generated.resources.action_test
import kaptionit.composeapp.generated.resources.app_title
import kaptionit.composeapp.generated.resources.confirm_delete_all
import kaptionit.composeapp.generated.resources.drop_zone_hint
import kaptionit.composeapp.generated.resources.state_downloading
import kaptionit.composeapp.generated.resources.status_free_space_prefix
import kaptionit.composeapp.generated.resources.status_model_prefix
import kaptionit.composeapp.generated.resources.task_list_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File

private val taskSortComparator =
    compareByDescending<TaskRecord> { it.phase.isActivelyProcessing() }.thenBy { it.createdAtMs }

@Composable
fun AppTaskScreen(
    tasks: List<TaskRecord>,
    onFileSelected: (File) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onStartAll: () -> Unit,
    onPauseAll: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val scope = rememberCoroutineScope()
    val currentOnFileSelected = rememberUpdatedState(onFileSelected)

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val data = event.dragData()
                if (data !is DragData.FilesList) return false
                val files = data.readFiles().mapNotNull { fileFromDragDropPath(it) }
                if (files.isEmpty()) return false
                files.forEach { currentOnFileSelected.value(it) }
                return true
            }
        }
    }

    Column(modifier.fillMaxHeight()) {
        DropZone(
            onChooseFile = {
                scope.launch {
                    val file = withContext(Dispatchers.IO) { pickVideoFileWithChooser() }
                    if (file != null) currentOnFileSelected.value(file)
                }
            },
            dropTarget = dropTarget,
        )

        if (tasks.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.app_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        } else {
            TaskListHeader(
                onStartAll = onStartAll,
                onPauseAll = onPauseAll,
                onDeleteAll = onDeleteAll,
            )

            val listState = rememberLazyListState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                    contentPadding = PaddingValues(top = 16.dp, end = 12.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        tasks.sortedWith(taskSortComparator),
                        key = { it.id },
                    ) { task ->
                        TaskRowCard(
                            task = task,
                            onDelete = { onDeleteTask(task.id) },
                            onRetry = { onRetryTask(task.id) },
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TaskListHeader(
    onStartAll: () -> Unit,
    onPauseAll: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .padding(top = spacing.xxLarge, bottom = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.task_list_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_start_all)) },
                    onClick = { menuExpanded = false; onStartAll() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_pause_all)) },
                    onClick = { menuExpanded = false; onPauseAll() },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.action_delete_all),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { menuExpanded = false; showDeleteConfirm = true },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeleteAll() }) {
                    Text(
                        stringResource(Res.string.action_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
            text = { Text(stringResource(Res.string.confirm_delete_all)) },
        )
    }
}

@Composable
private fun DropZone(
    onChooseFile: () -> Unit,
    dropTarget: DragAndDropTarget,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 160.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)
            .padding(spacing.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            )
            Text(stringResource(Res.string.drop_zone_hint))
            OutlinedButton(onClick = onChooseFile) {
                Text(stringResource(Res.string.action_choose_file))
            }
        }
    }
}

@Composable
fun StatusBarRow(
    selectedPresetId: String,
    modelDownload: ModelDownloadUiState,
    onTranslationTestClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val home = remember { File(System.getProperty("user.home") ?: ".") }
    val freeGb = remember(home) {
        "%.0f GB".format(home.usableSpace / (1024.0 * 1024 * 1024))
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (modelDownload.active) {
            val pct =
                if (modelDownload.progress > 0f) "${(modelDownload.progress * 100f).toInt()}%" else ""
            val line = buildString {
                append(stringResource(Res.string.state_downloading))
                append(modelDownload.fileName)
                if (modelDownload.message.isNotBlank()) append(" ${modelDownload.message}")
                if (pct.isNotEmpty()) append(" $pct")
            }
            Text(
                line,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.status_model_prefix, selectedPresetId),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(Res.string.status_free_space_prefix, freeGb),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onTranslationTestClick) {
                Text(
                    stringResource(Res.string.action_test),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            TextButton(onClick = onSettingsClick) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
                ) {
                    Text("⚙", style = MaterialTheme.typography.labelMedium)
                    Text(
                        stringResource(Res.string.action_settings),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AppTaskScreenPreview() {
    KaptionItTheme {
        AppTaskScreen(
            tasks = listOf(
                TaskRecord(
                    id = "1",
                    fileName = "video_a.mp4",
                    phase = PipelinePhase.Transcribing,
                    progress = 0.6f,
                    message = "Transcribing…"
                ),
                TaskRecord(
                    id = "2",
                    fileName = "video_b.mp4",
                    phase = PipelinePhase.Done,
                    progress = 1f,
                    outputPath = "/tmp/b.srt"
                ),
                TaskRecord(
                    id = "3",
                    fileName = "video_c.mp4",
                    phase = PipelinePhase.Queued,
                    message = "Queued"
                ),
            ),
            onFileSelected = {},
            onDeleteTask = {},
            onRetryTask = {},
            onStartAll = {},
            onPauseAll = {},
            onDeleteAll = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun StatusBarRowPreview() {
    KaptionItTheme {
        StatusBarRow(
            selectedPresetId = "base",
            modelDownload = ModelDownloadUiState(),
            onTranslationTestClick = {},
            onSettingsClick = {},
        )
    }
}
