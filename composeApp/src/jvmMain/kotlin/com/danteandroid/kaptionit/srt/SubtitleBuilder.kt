package com.danteandroid.kaptionit.srt

import com.danteandroid.kaptionit.settings.ToolingSettings
import com.danteandroid.kaptionit.translate.TargetLanguageMapper
import com.danteandroid.kaptionit.translate.TranslationMetrics
import com.danteandroid.kaptionit.translate.TranslationService
import com.danteandroid.kaptionit.ui.TranslationTaskStats
import com.danteandroid.kaptionit.utils.JvmResourceStrings
import com.danteandroid.kaptionit.whisper.WhisperParseResult
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.err_no_segments
import kaptionit.composeapp.generated.resources.msg_skip_translate

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
            onProgressUpdate(0.8f, JvmResourceStrings.text(Res.string.msg_skip_translate))
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
