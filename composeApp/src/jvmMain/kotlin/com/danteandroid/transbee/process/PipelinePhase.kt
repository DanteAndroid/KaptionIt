package com.danteandroid.transbee.process

enum class PipelinePhase {
    Idle,
    Queued,
    Extracting,
    Transcribing,
    Translating,
    Done,
    Failed,
    Cancelled,
}

/** 提取/转录/翻译进行中：任务列表排序置顶、任务卡片显示进度条 */
fun PipelinePhase.isActivelyProcessing(): Boolean =
    this == PipelinePhase.Extracting ||
        this == PipelinePhase.Transcribing ||
        this == PipelinePhase.Translating

/** 队列中或处理中：可被「全部停止」打断并标记为已取消 */
fun PipelinePhase.isCancellableByStopAll(): Boolean =
    this == PipelinePhase.Queued || isActivelyProcessing()
