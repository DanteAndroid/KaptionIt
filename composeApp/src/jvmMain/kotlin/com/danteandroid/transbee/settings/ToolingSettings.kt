package com.danteandroid.transbee.settings

import com.danteandroid.transbee.translate.TranslationEngine
import com.danteandroid.transbee.utils.OsUtils
import kotlinx.serialization.Serializable

@Serializable
enum class PdfTranslateFormat {
    /** 仅原文 */
    SOURCE_ONLY,

    /** 一段原文紧跟一段译文 */
    BILINGUAL,

    /** 全部原文在前，全部译文在后 */
    ORIGINAL_FIRST,

    /** 全部译文在前，全部原文在后 */
    TRANSLATION_FIRST,
}

@Serializable
data class ForcedTranslationTerm(
    val source: String = "",
    val target: String = "",
)

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
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.1-flash-lite-preview",
    val openAiKey: String = "",
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val openAiModel: String = "gpt-5-mini",
    val targetLanguage: String = "简体中文",
    /** 兼容旧版本字段：翻译阶段强制保留原文（不翻译）的词汇 */
    val noTranslateTerms: List<String> = emptyList(),
    /** 翻译阶段指定词汇映射：source -> target */
    val forcedTranslationTerms: List<ForcedTranslationTerm> = emptyList(),
    /** 兼容旧版本字段：升级后会被拆分进上面两个列表 */
    val translationGlossaryTerms: List<String> = emptyList(),
    val minerUToken: String = "",
    val pdfTranslateFormat: PdfTranslateFormat = PdfTranslateFormat.BILINGUAL,
    val sidebarExpanded: Boolean = true,
    val translationPrompt: String = "",
    val useTranscriptionCache: Boolean = true,
) {
    fun normalized(): ToolingSettings {
        val normalizedOutputs = subtitleOutputs
            .map { it.trim().lowercase() }
            .filter { it == "source" || it == "target" || it == "bilingual_single" }
            .distinct()
        val safeOutputs = if (normalizedOutputs.isEmpty()) listOf("source") else normalizedOutputs
        val safeBaseUrl = openAiBaseUrl.trim().ifEmpty { "https://api.openai.com/v1" }
        val safeModel = openAiModel.trim().lowercase().ifEmpty { "gpt-5-mini" }
        val safeGeminiModel = geminiModel.trim().ifEmpty { "gemini-3.1-flash-lite-preview" }
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
        val legacyTerms = translationGlossaryTerms
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val safeNoTranslateTerms = (if (noTranslateTerms.isEmpty()) legacyTerms else noTranslateTerms)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val safeForcedTranslationTerms = (
            if (forcedTranslationTerms.isEmpty()) {
                safeNoTranslateTerms.map { ForcedTranslationTerm(source = it, target = it) }
            } else {
                forcedTranslationTerms
            }
        ).mapNotNull { item ->
            val source = item.source.trim()
            val target = item.target.trim()
            if (source.isEmpty() || target.isEmpty()) null else ForcedTranslationTerm(source = source, target = target)
        }.distinctBy { it.source.lowercase() to it.target }
        return copy(
            whisperLanguage = safeWhisperLang,
            subtitleOutputs = safeOutputs,
            openAiBaseUrl = safeBaseUrl,
            openAiModel = safeModel,
            geminiModel = safeGeminiModel,
            targetLanguage = safeTarget,
            translationEngine = safeEngine,
            noTranslateTerms = safeNoTranslateTerms,
            forcedTranslationTerms = safeForcedTranslationTerms,
            translationGlossaryTerms = emptyList(),
            translationPrompt = translationPrompt.trim(),
            useTranscriptionCache = useTranscriptionCache,
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
            geminiApiKey = System.getenv("GEMINI_API_KEY").orEmpty(),
            openAiKey = System.getenv("OPENAI_API_KEY").orEmpty(),
            targetLanguage = "简体中文",
        )
    }
}
