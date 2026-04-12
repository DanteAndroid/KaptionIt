@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
)

package com.danteandroid.transbee.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.danteandroid.transbee.AppTheme
import com.danteandroid.transbee.TransbeeTheme
import com.danteandroid.transbee.process.PipelinePhase
import com.danteandroid.transbee.process.isActivelyProcessing
import com.danteandroid.transbee.ui.ModelDownloadUiState
import com.danteandroid.transbee.ui.TaskRecord
import com.danteandroid.transbee.utils.fileFromDragDropPath
import com.danteandroid.transbee.utils.JvmResourceStrings
import com.danteandroid.transbee.utils.pickFilesWithChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.action_cancel
import transbee.composeapp.generated.resources.action_choose_file
import transbee.composeapp.generated.resources.action_confirm
import transbee.composeapp.generated.resources.action_delete_all
import transbee.composeapp.generated.resources.action_pause_all
import transbee.composeapp.generated.resources.action_settings
import transbee.composeapp.generated.resources.action_start_all
import transbee.composeapp.generated.resources.action_translation_service
import transbee.composeapp.generated.resources.app_title
import transbee.composeapp.generated.resources.confirm_delete_all
import transbee.composeapp.generated.resources.drag_to_start_hint
import transbee.composeapp.generated.resources.drop_zone_hint
import transbee.composeapp.generated.resources.state_downloading
import transbee.composeapp.generated.resources.tasks_completed
import transbee.composeapp.generated.resources.tasks_processing
import transbee.composeapp.generated.resources.drop_zone_hint_formats
import transbee.composeapp.generated.resources.preview_queued
import transbee.composeapp.generated.resources.preview_transcribing
import transbee.composeapp.generated.resources.symbol_settings_gear
import java.io.File

private val taskSortComparator =
    compareByDescending<TaskRecord> { it.phase.isActivelyProcessing() }.thenBy { it.createdAtMs }

private val completedTaskTimeDescending: Comparator<TaskRecord> = compareByDescending { task ->
    val t = task.completedAtMs
    if (t > 0L) t else task.createdAtMs
}

private fun partitionTasksForUi(tasks: List<TaskRecord>): Pair<List<TaskRecord>, List<TaskRecord>> {
    val processing = ArrayList<TaskRecord>(tasks.size)
    val completed = ArrayList<TaskRecord>()
    for (t in tasks) {
        if (t.phase == PipelinePhase.Done) completed.add(t) else processing.add(t)
    }
    processing.sortWith(taskSortComparator)
    completed.sortWith(completedTaskTimeDescending)
    return processing to completed
}

/** 与翻译服务字幕区一致：描边 + 淡黄底（随主题 primary） */
@Composable
internal fun SubtitleAccentOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        contentPadding = contentPadding,
    ) {
        ProvideTextStyle(
            value = MaterialTheme.typography.labelLarge.copy(color = Color.Unspecified),
        ) {
            content()
        }
    }
}

private val allowedExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", // Video
    "mp3", "wav", "aac", "flac", "m4a", "ogg", "wma", // Audio
    "pdf", "doc", "docx", "ppt", "pptx",               // Documents (MinerU)
    "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", // Images (MinerU)
    "txt", "md",                                        // Text (direct translate)
)

@Composable
fun AppTaskScreen(
    tasks: List<TaskRecord>,
    onFilesSelected: (List<File>) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onStartAll: () -> Unit,
    onPauseAll: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val scope = rememberCoroutineScope()
    val currentOnFilesSelected = rememberUpdatedState(onFilesSelected)

    var isDragging by remember { mutableStateOf(false) }

    val handleFilesSelection = { files: List<File> ->
        currentOnFilesSelected.value(files)
    }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false
                val data = event.dragData()
                if (data !is DragData.FilesList) return false
                val files = data.readFiles().mapNotNull { fileFromDragDropPath(it) }
                if (files.isEmpty()) return false
                handleFilesSelection(files)
                return true
            }
        }
    }

    Box(
        modifier
            .fillMaxHeight()
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (tasks.isEmpty()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            stringResource(Res.string.drop_zone_hint),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(Res.string.drop_zone_hint_formats),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Spacer(Modifier.height(40.dp))
                        SubtitleAccentOutlinedButton(
                            onClick = {
                                scope.launch {
                                    val files = withContext(Dispatchers.IO) { pickFilesWithChooser() }
                                    if (files.isNotEmpty()) handleFilesSelection(files)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                stringResource(Res.string.action_choose_file),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    TaskListHeader(
                        onChooseFiles = {
                            scope.launch {
                                val files = withContext(Dispatchers.IO) { pickFilesWithChooser() }
                                if (files.isNotEmpty()) handleFilesSelection(files)
                            }
                        },
                        onStartAll = onStartAll,
                        onPauseAll = onPauseAll,
                        onDeleteAll = onDeleteAll,
                    )
                }

                val (processingTasks, completedTasks) = remember(tasks) {
                    partitionTasksForUi(tasks)
                }

                val listState = rememberLazyListState()

                // 自动滚动：仅当「处理中」集合在已有快照基础上出现新 id 时滚动（冷启动首次加载不动画，避免底部/列表抽动）
                val processingTaskIds = remember(processingTasks) { processingTasks.map { it.id }.toSet() }
                var lastProcessingTaskIds by remember { mutableStateOf(emptySet<String>()) }
                LaunchedEffect(processingTaskIds) {
                    val newIds = processingTaskIds - lastProcessingTaskIds
                    if (newIds.isNotEmpty() && lastProcessingTaskIds.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                    lastProcessingTaskIds = processingTaskIds
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                        contentPadding = PaddingValues(top = 8.dp, end = 12.dp, bottom = 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (processingTasks.isNotEmpty()) {
                            item(key = "section_processing") {
                                TaskSectionHeader(stringResource(Res.string.tasks_processing))
                            }
                            items(
                                processingTasks,
                                key = { it.id },
                            ) { task ->
                                TaskRowCard(
                                    task = task,
                                    onDelete = { onDeleteTask(task.id) },
                                    onRetry = { onRetryTask(task.id) },
                                )
                            }
                        }

                        if (completedTasks.isNotEmpty()) {
                            item(key = "section_completed") {
                                TaskSectionHeader(
                                    stringResource(Res.string.tasks_completed),
                                    Modifier.padding(top = spacing.medium)
                                )
                            }
                            items(
                                completedTasks,
                                key = { it.id },
                            ) { task ->
                                TaskRowCard(
                                    task = task,
                                    onDelete = { onDeleteTask(task.id) },
                                    onRetry = { onRetryTask(task.id) },
                                )
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }

        if (isDragging) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(spacing.medium))
                        Text(
                            stringResource(Res.string.drag_to_start_hint),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large)
            .padding(bottom = spacing.small),
    )
}

@Composable
private fun TaskListHeader(
    onChooseFiles: () -> Unit,
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
            .padding(end = spacing.large, bottom = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.app_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.8.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = spacing.large),
        )
        Spacer(Modifier.weight(1f))
        SubtitleAccentOutlinedButton(
            onClick = onChooseFiles,
            modifier = Modifier.height(38.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 14.dp, top = 0.dp, bottom = 0.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(Res.string.action_choose_file),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
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
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 160.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
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
            SubtitleAccentOutlinedButton(onClick = onChooseFile) {
                Text(stringResource(Res.string.action_choose_file))
            }
        }
    }
}

@Composable
fun StatusBarRow(
    modelDownload: ModelDownloadUiState,
    onTranslationServiceClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = spacing.large, top = 12.dp, end = spacing.large, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
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
            Spacer(Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onTranslationServiceClick,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Text(
                    stringResource(Res.string.action_translation_service),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            OutlinedButton(
                onClick = onSettingsClick,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
                ) {
                    Text(stringResource(Res.string.symbol_settings_gear), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(Res.string.action_settings),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AppTaskScreenPreview() {
    TransbeeTheme {
        AppTaskScreen(
            tasks = listOf(
                TaskRecord(
                    id = "1",
                    fileName = "video_a.mp4",
                    phase = PipelinePhase.Transcribing,
                    progress = 0.6f,
                    message = JvmResourceStrings.text(Res.string.preview_transcribing)
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
                    message = JvmResourceStrings.text(Res.string.preview_queued)
                ),
            ),
            onFilesSelected = {},
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
    TransbeeTheme {
        StatusBarRow(
            modelDownload = ModelDownloadUiState(),
            onTranslationServiceClick = {},
            onSettingsClick = {},
        )
    }
}
