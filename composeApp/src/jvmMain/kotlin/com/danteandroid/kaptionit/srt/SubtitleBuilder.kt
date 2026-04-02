package com.danteandroid.transbee.srt

import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.translate.TargetLanguageMapper
import com.danteandroid.transbee.translate.TranslationMetrics
import com.danteandroid.transbee.translate.TranslationService
import com.danteandroid.transbee.ui.TranslationTaskStats
import com.danteandroid.transbee.utils.JvmResourceStrings
import com.danteandroid.transbee.whisper.WhisperParseResult
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_no_segments
import transbee.composeapp.generated.resources.msg_skip_translate

data class SubtitleBuildResult(
    val files: List<SubtitleExporter.ExportFile>,
    val translationStats: TranslationTaskStats?,
)

object SubtitleBuilder {

    suspend fun buildSubtitleExportFiles(
        cfg: ToolingSettings,
        whisperDoc: WhisperParseResult,
        recognitionDurationMs: Long,
        onProgressUpdate: (progress: Float, message: String) -> Unit
    ): SubtitleBuildResult {
        val segments = whisperDoc.segments
        if (segments.isEmpty()) {
            error(JvmResourceStrings.text(Res.string.err_no_segments))
        }
        val requestedOutputs = cfg.subtitleOutputs.toSet()
        val effectiveOutputs = if (requestedOutputs.isEmpty()) setOf("source") else requestedOutputs
        val needsTranslation = effectiveOutputs.contains("target") || effectiveOutputs.contains("bilingual_single")
        val lineCount = segments.size
        val translated: List<String>
        val stats: TranslationTaskStats?

        if (!needsTranslation) {
            onProgressUpdate(1f, JvmResourceStrings.text(Res.string.msg_skip_translate))
            translated = segments.map { it.text }
            stats = TranslationTaskStats(
                recognitionDurationMs = recognitionDurationMs,
                translationDurationMs = 0L,
                lineCount = lineCount,
                requestCount = 0,
                retryCount = 0,
                skipped = true,
            )
        } else {
            val metrics = TranslationMetrics()
            val t0 = System.currentTimeMillis()
            translated = TranslationService.translateSegments(
                cfg = cfg,
                whisperDoc = whisperDoc,
                segments = segments,
                metrics = metrics,
                onProgressUpdate = onProgressUpdate
            )
            val translationDurationMs = System.currentTimeMillis() - t0
            stats = TranslationTaskStats(
                recognitionDurationMs = recognitionDurationMs,
                translationDurationMs = translationDurationMs,
                lineCount = lineCount,
                requestCount = metrics.requestCount.get(),
                retryCount = metrics.retryCount.get(),
                skipped = false,
            )
        }

        val files = SubtitleExporter.exportFiles(
            segments = segments,
            translations = translated,
            format = cfg.exportFormat,
            subtitleOutputs = effectiveOutputs,
            targetSuffix = TargetLanguageMapper.subtitleTargetSuffix(cfg.targetLanguage),
        )
        return SubtitleBuildResult(files = files, translationStats = stats)
    }
}
