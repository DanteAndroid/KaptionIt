package com.danteandroid.kaptionit.ui

import androidx.compose.runtime.Composable
import com.danteandroid.kaptionit.process.PipelinePhase
import com.danteandroid.kaptionit.translate.TranslationEngine
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.engine_apple
import kaptionit.composeapp.generated.resources.engine_deepl
import kaptionit.composeapp.generated.resources.engine_google
import kaptionit.composeapp.generated.resources.engine_openai
import kaptionit.composeapp.generated.resources.format_srt
import kaptionit.composeapp.generated.resources.format_txt
import kaptionit.composeapp.generated.resources.format_vtt
import kaptionit.composeapp.generated.resources.lang_de
import kaptionit.composeapp.generated.resources.lang_en
import kaptionit.composeapp.generated.resources.lang_es
import kaptionit.composeapp.generated.resources.lang_fr
import kaptionit.composeapp.generated.resources.lang_ja
import kaptionit.composeapp.generated.resources.lang_ko
import kaptionit.composeapp.generated.resources.lang_whisper_auto
import kaptionit.composeapp.generated.resources.lang_zh_cn
import kaptionit.composeapp.generated.resources.lang_zh_tw
import kaptionit.composeapp.generated.resources.phase_cancelled
import kaptionit.composeapp.generated.resources.phase_done
import kaptionit.composeapp.generated.resources.phase_extracting
import kaptionit.composeapp.generated.resources.phase_failed
import kaptionit.composeapp.generated.resources.phase_queued
import kaptionit.composeapp.generated.resources.phase_transcribing
import kaptionit.composeapp.generated.resources.phase_translating
import kaptionit.composeapp.generated.resources.subtitle_output_bilingual_single
import kaptionit.composeapp.generated.resources.subtitle_output_source
import kaptionit.composeapp.generated.resources.subtitle_output_target
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 将业务枚举映射到 Compose Multiplatform Resources 中的 [StringResource]，
 * 供 UI 使用 [stringResource] 显示本地化文案。
 */
enum class ExportFormat(val id: String) {
    SRT("srt"),
    VTT("vtt"),
    TXT("txt"),
    ;

    companion object {
        fun fromId(id: String): ExportFormat =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: SRT
    }

    val labelRes: StringResource
        get() = when (this) {
            SRT -> Res.string.format_srt
            VTT -> Res.string.format_vtt
            TXT -> Res.string.format_txt
        }
}

enum class SubtitleOutputKind(val id: String) {
    SOURCE("source"),
    TARGET("target"),
    BILINGUAL_SINGLE("bilingual_single"),
    ;

    companion object {
        fun fromId(id: String): SubtitleOutputKind? = entries.find { it.id == id }
    }

    val labelRes: StringResource
        get() = when (this) {
            SOURCE -> Res.string.subtitle_output_source
            TARGET -> Res.string.subtitle_output_target
            BILINGUAL_SINGLE -> Res.string.subtitle_output_bilingual_single
        }
}

val TranslationEngine.labelRes: StringResource
    get() = when (this) {
        TranslationEngine.APPLE -> Res.string.engine_apple
        TranslationEngine.GOOGLE -> Res.string.engine_google
        TranslationEngine.DEEPL -> Res.string.engine_deepl
        TranslationEngine.OPENAI -> Res.string.engine_openai
    }

val PipelinePhase.label: String
    @Composable get() = when (this) {
        PipelinePhase.Idle -> ""
        PipelinePhase.Queued -> stringResource(Res.string.phase_queued)
        PipelinePhase.Extracting -> stringResource(Res.string.phase_extracting)
        PipelinePhase.Transcribing -> stringResource(Res.string.phase_transcribing)
        PipelinePhase.Translating -> stringResource(Res.string.phase_translating)
        PipelinePhase.Done -> stringResource(Res.string.phase_done)
        PipelinePhase.Failed -> stringResource(Res.string.phase_failed)
        PipelinePhase.Cancelled -> stringResource(Res.string.phase_cancelled)
    }

data class TargetLanguageOption(
    val id: String,
    val labelRes: StringResource,
)

val targetLanguageOptions: List<TargetLanguageOption> = listOf(
    TargetLanguageOption("简体中文", Res.string.lang_zh_cn),
    TargetLanguageOption("繁体中文", Res.string.lang_zh_tw),
    TargetLanguageOption("英语", Res.string.lang_en),
    TargetLanguageOption("日语", Res.string.lang_ja),
    TargetLanguageOption("韩语", Res.string.lang_ko),
    TargetLanguageOption("法语", Res.string.lang_fr),
    TargetLanguageOption("德语", Res.string.lang_de),
    TargetLanguageOption("西班牙语", Res.string.lang_es),
)

data class WhisperTranscriptionLanguageOption(
    val whisperCode: String,
    val labelRes: StringResource,
)

val whisperTranscriptionLanguageOptions: List<WhisperTranscriptionLanguageOption> = listOf(
    WhisperTranscriptionLanguageOption("auto", Res.string.lang_whisper_auto),
    WhisperTranscriptionLanguageOption("ja", Res.string.lang_ja),
    WhisperTranscriptionLanguageOption("en", Res.string.lang_en),
    WhisperTranscriptionLanguageOption("zh", Res.string.lang_zh_cn),
    WhisperTranscriptionLanguageOption("ko", Res.string.lang_ko),
    WhisperTranscriptionLanguageOption("fr", Res.string.lang_fr),
    WhisperTranscriptionLanguageOption("de", Res.string.lang_de),
    WhisperTranscriptionLanguageOption("es", Res.string.lang_es),
)
