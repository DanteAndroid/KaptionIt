package com.danteandroid.transbee.settings

import com.danteandroid.transbee.translate.TranslationEngine
import com.danteandroid.transbee.utils.OsUtils
import kotlinx.serialization.Serializable

@Serializable
enum class PdfTranslateFormat {
    /** 一段原文紧跟一段译文 */
    BILINGUAL,

    /** 全部原文在前，全部译文在后 */
    ORIGINAL_FIRST,

    /** 全部译文在前，全部原文在后 */
    TRANSLATION_FIRST,
}

@Serializable
data class ToolingSettings(
    val whisperModel: String = "",
    val whisperLanguage: String = "auto",
    /** whisper-cli `--vad`：需本机已下载 Silero VAD 模型；默认开启 */
    val whisperVadEnabled: Boolean = true,
    val exportFormat: String = "srt",
    val subtitleOutputs: List<String> = listOf("source", "target"),
    val translationEngine: TranslationEngine = TranslationEngine.APPLE,
    val appleTranslateBinary: String = "",
    val googleApiKey: String = "",
    val deeplApiKey: String = "",
    val deeplUseFreeApi: Boolean = true,
    val openAiKey: String = "",
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val openAiModel: String = "gpt-5-mini",
    val targetLanguage: String = "简体中文",
    val minerUToken: String = "",
    val pdfTranslateFormat: PdfTranslateFormat = PdfTranslateFormat.BILINGUAL,
) {
    fun normalized(): ToolingSettings {
        val normalizedOutputs = subtitleOutputs
            .map { it.trim().lowercase() }
            .filter { it == "source" || it == "target" || it == "bilingual_single" }
            .distinct()
        val safeOutputs = if (normalizedOutputs.isEmpty()) listOf("source") else normalizedOutputs
        val safeBaseUrl = openAiBaseUrl.trim().ifEmpty { "https://api.openai.com/v1" }
        val safeModel = openAiModel.trim().lowercase().ifEmpty { "gpt-5-mini" }
        val safeTarget = if (targetLanguage.trim() == "不翻译" || targetLanguage.isBlank()) {
            "简体中文"
        } else {
            targetLanguage
        }
        val safeEngine = if (translationEngine == TranslationEngine.APPLE && !OsUtils.isMacOs()) {
            TranslationEngine.OPENAI
        } else {
            translationEngine
        }
        val safeWhisperLang = whisperLanguage.trim().lowercase().ifEmpty { "auto" }
        return copy(
            whisperLanguage = safeWhisperLang,
            subtitleOutputs = safeOutputs,
            openAiBaseUrl = safeBaseUrl,
            openAiModel = safeModel,
            targetLanguage = safeTarget,
            translationEngine = safeEngine,
        )
    }

    companion object {
        fun fromEnvironment(): ToolingSettings = ToolingSettings(
            translationEngine = TranslationEngine.APPLE,
            appleTranslateBinary = System.getenv("APPLE_TRANSLATE_BINARY").orEmpty(),
            googleApiKey = System.getenv("GOOGLE_TRANSLATE_API_KEY")
                ?: System.getenv("GOOGLE_API_KEY").orEmpty(),
            deeplApiKey = System.getenv("DEEPL_AUTH_KEY")
                ?: System.getenv("DEEPL_FREE_AUTH_KEY").orEmpty(),
            deeplUseFreeApi = System.getenv("DEEPL_PRO")?.equals("1", ignoreCase = true) != true,
            openAiKey = System.getenv("OPENAI_API_KEY").orEmpty(),
            targetLanguage = "简体中文",
        )
    }
}
