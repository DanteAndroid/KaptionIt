package com.danteandroid.transbee.ui

import com.danteandroid.transbee.process.PipelinePhase

data class TaskRecord(
    val id: String,
    val fileName: String,
    val sourcePath: String? = null,
    val phase: PipelinePhase = PipelinePhase.Idle,
    val progress: Float = 0f,
    val progressIndeterminate: Boolean = false,
    val message: String = "",
    val outputPath: String? = null,
    val error: String? = null,
    /** 任务成功完成后：翻译耗时、条数、请求与重试（仅本次任务） */
    val translationStats: TranslationTaskStats? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
)
