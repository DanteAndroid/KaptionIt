package com.danteandroid.transbee.ui

data class TranslationTaskStats(
    /** 提取音频 + Whisper 识别（命中缓存时为 0） */
    val recognitionDurationMs: Long,
    val translationDurationMs: Long,
    val lineCount: Int,
    val requestCount: Int,
    val retryCount: Int,
    /** 仅导出原文、未走翻译引擎 */
    val skipped: Boolean = false,
)
