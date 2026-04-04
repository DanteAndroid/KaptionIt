package com.danteandroid.transbee.utils

import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.translate.AppleTranslateBinary
import com.danteandroid.transbee.translate.AppleTranslator
import com.danteandroid.transbee.translate.DeepLTranslator
import com.danteandroid.transbee.translate.GoogleTranslator
import com.danteandroid.transbee.translate.OpenAiTranslator
import com.danteandroid.transbee.translate.TargetLanguageMapper
import com.danteandroid.transbee.translate.TranslationEngine
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_apple_translate_macos_only
import transbee.composeapp.generated.resources.test_err_apple_binary
import transbee.composeapp.generated.resources.test_err_deepl_key
import transbee.composeapp.generated.resources.test_err_google_key
import transbee.composeapp.generated.resources.test_err_openai_key

/** 单行 20 个英文字符：多义词、领域缩写，用于翻译服务弹窗默认原文 */
const val TRANSLATION_SERVICE_DEFAULT_SOURCE_EN = "Polysemy breaks NMT."

private const val ENGLISH_SMOKE_TEXT =
    "Classical mythology and modern science intertwine to form a labyrinth of thought. " +
    "The will of the gods symbolizes the unknowable laws of nature, the choices of heroes reflect " +
    "the conflicts deep within the psyche, and the paths of the stars represent both astronomical " +
    "principles and metaphors for human cognitive order. In such a context, words convey not only " +
    "information but also culture and philosophical reflection. Translating texts of this kind demands " +
    "preserving the tension of metaphor while recreating the balance of logic and emotion in another " +
    "language. Subtle tones, rhetorical devices, and historical references carry meaning; any deviation " +
    "may alter the original intent. It requires the translator to possess a high degree of cultural " +
    "sensitivity and philosophical insight."

private const val CHINESE_SMOKE_TEXT =
    "古典神话与现代科学相互交织，构成一座思想的迷宫。众神的意志象征着自然界不可知的规律，" +
    "英雄的选择折射出心理深处的冲突，而星辰的轨迹则同时代表着天文学原理与人类认知秩序的隐喻。" +
    "在这样的语境下，语言不仅传递信息，更承载着文化与哲学的反思。翻译此类文本，" +
    "需要在保留隐喻张力的同时，在另一种语言中重建逻辑与情感的平衡。" +
    "细微的语气、修辞手法与历史典故都蕴含着意义，任何偏差都可能改变原意。" +
    "这要求译者具备高度的文化敏感性与哲学洞察力。"

data class SmokeTestResult(val original: String, val translated: String)

fun smokeTestSourceText(tooling: ToolingSettings): String {
    val target = tooling.targetLanguage
    return when (tooling.translationEngine) {
        TranslationEngine.APPLE -> {
            val targetLocale = TargetLanguageMapper.toAppleLocale(target, forTarget = true)
            val sourceLocale = if (targetLocale.startsWith("en", ignoreCase = true)) "zh-Hans" else "en"
            if (sourceLocale.startsWith("zh")) CHINESE_SMOKE_TEXT else ENGLISH_SMOKE_TEXT
        }

        TranslationEngine.GOOGLE -> {
            val targetCode = TargetLanguageMapper.toGoogleTargetCode(target)
            if (targetCode.startsWith("en", ignoreCase = true)) CHINESE_SMOKE_TEXT else ENGLISH_SMOKE_TEXT
        }

        TranslationEngine.DEEPL -> {
            val targetCode = TargetLanguageMapper.toDeepLTargetCode(target)
            if (targetCode.startsWith("EN", ignoreCase = true)) CHINESE_SMOKE_TEXT else ENGLISH_SMOKE_TEXT
        }

        TranslationEngine.OPENAI -> {
            val isEnTarget = target.contains("英语") || target.equals("English", ignoreCase = true)
            if (isEnTarget) CHINESE_SMOKE_TEXT else ENGLISH_SMOKE_TEXT
        }
    }
}

suspend fun runServiceSmokeTest(tooling: ToolingSettings, testText: String): SmokeTestResult {
    val target = tooling.targetLanguage
    // 各引擎按目标语种选用中/英测试段，避免目标与源相同导致「假成功」
    return when (tooling.translationEngine) {
        TranslationEngine.APPLE -> {
            if (!OsUtils.isMacOs()) {
                error(JvmResourceStrings.text(Res.string.err_apple_translate_macos_only))
            }
            val bin = AppleTranslateBinary.resolvePath(tooling.appleTranslateBinary)
                ?: error(JvmResourceStrings.text(Res.string.test_err_apple_binary))
            val targetLocale = TargetLanguageMapper.toAppleLocale(target, forTarget = true)
            val sourceLocale = if (targetLocale.startsWith("en", ignoreCase = true)) "zh-Hans" else "en"
            val translated = AppleTranslator(binaryPath = bin).translateBatch(
                listOf(testText),
                sourceAppleLocale = sourceLocale,
                targetAppleLocale = targetLocale,
                timeoutMs = 20_000L,
            )
            SmokeTestResult(testText, translated.firstOrNull().orEmpty())
        }

        TranslationEngine.GOOGLE -> {
            if (tooling.googleApiKey.isBlank()) error(JvmResourceStrings.text(Res.string.test_err_google_key))
            val targetCode = TargetLanguageMapper.toGoogleTargetCode(target)
            val translated = GoogleTranslator(apiKey = tooling.googleApiKey).translateBatch(listOf(testText), targetCode)
            SmokeTestResult(testText, translated.firstOrNull().orEmpty())
        }

        TranslationEngine.DEEPL -> {
            if (tooling.deeplApiKey.isBlank()) error(JvmResourceStrings.text(Res.string.test_err_deepl_key))
            val targetCode = TargetLanguageMapper.toDeepLTargetCode(target)
            val translated = DeepLTranslator(
                authKey = tooling.deeplApiKey,
                useFreeApiHost = tooling.deeplUseFreeApi,
            ).translateBatch(listOf(testText), targetCode)
            SmokeTestResult(testText, translated.firstOrNull().orEmpty())
        }

        TranslationEngine.OPENAI -> {
            if (tooling.openAiKey.isBlank()) error(JvmResourceStrings.text(Res.string.test_err_openai_key))
            val translated = OpenAiTranslator(
                apiKey = tooling.openAiKey,
                model = tooling.openAiModel,
                baseUrl = tooling.openAiBaseUrl,
            ).translateBatch(listOf(testText), target)
            SmokeTestResult(testText, translated.firstOrNull().orEmpty())
        }
    }
}
