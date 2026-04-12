package com.danteandroid.transbee.settings

import com.danteandroid.transbee.translate.TranslationEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val knownJsonKeys = setOf(
    "translationEngine",
    "engine",
    "openAiKey",
    "openai_key",
    "openaiKey",
    "openAiBaseUrl",
    "openAiModel",
    "googleApiKey",
    "google_api_key",
    "deeplApiKey",
    "deepl_api_key",
    "deeplKey",
    "geminiApiKey",
    "gemini_api_key",
    "geminiModel",
    "deeplUseFreeApi",
    "targetLanguage",
    "appleTranslateBinary",
    "key",
    "apiKey",
)

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.stringFromKeys(vararg keys: String): String? {
    for (k in keys) {
        stringOrNull(k)?.let { return it }
    }
    return null
}

private fun JsonPrimitive.booleanLenient(): Boolean? =
    contentOrNull?.trim()?.lowercase()?.let { s ->
        when (s) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
    }

/**
 * 将购买校验返回的文本合并进设置：支持纯文本 API Key（默认走 OpenAI 兼容），或 JSON 指定引擎与各密钥字段。
 */
fun ToolingSettings.mergePurchasedConfiguration(raw: String): ToolingSettings {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return this
    val parsed = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
    if (parsed !is JsonObject) {
        return copy(
            translationEngine = TranslationEngine.OPENAI,
            openAiKey = trimmed,
        ).normalized()
    }
    if (parsed.isEmpty()) {
        return copy(
            translationEngine = TranslationEngine.OPENAI,
            openAiKey = trimmed,
        ).normalized()
    }
    val onlyUnknownKeys = parsed.keys.all { it !in knownJsonKeys }
    if (onlyUnknownKeys) {
        val single = parsed.values.singleOrNull()?.jsonPrimitive?.contentOrNull?.trim()
        if (single != null) {
            return copy(
                translationEngine = TranslationEngine.OPENAI,
                openAiKey = single,
            ).normalized()
        }
        return copy(
            translationEngine = TranslationEngine.OPENAI,
            openAiKey = trimmed,
        ).normalized()
    }
    var next = this
    parsed.stringFromKeys("translationEngine", "engine")?.let { name ->
        TranslationEngine.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { eng ->
            next = next.copy(translationEngine = eng)
        }
    }
    parsed.stringFromKeys("openAiKey", "openai_key", "openaiKey", "key", "apiKey")?.let {
        next = next.copy(openAiKey = it)
    }
    parsed.stringOrNull("openAiBaseUrl")?.let { next = next.copy(openAiBaseUrl = it) }
    parsed.stringOrNull("openAiModel")?.let { next = next.copy(openAiModel = it) }
    parsed.stringFromKeys("googleApiKey", "google_api_key")?.let { next = next.copy(googleApiKey = it) }
    parsed.stringFromKeys("deeplApiKey", "deepl_api_key", "deeplKey")?.let { next = next.copy(deeplApiKey = it) }
    parsed.stringFromKeys("geminiApiKey", "gemini_api_key")?.let { next = next.copy(geminiApiKey = it) }
    parsed.stringOrNull("geminiModel")?.let { next = next.copy(geminiModel = it) }
    parsed["deeplUseFreeApi"]?.jsonPrimitive?.booleanLenient()?.let {
        next = next.copy(deeplUseFreeApi = it)
    }
    parsed.stringOrNull("targetLanguage")?.let { next = next.copy(targetLanguage = it) }
    parsed.stringOrNull("appleTranslateBinary")?.let { next = next.copy(appleTranslateBinary = it) }
    if (next.openAiKey.isBlank() && parsed.stringOrNull("openAiKey") == null) {
        val fallback = parsed.stringFromKeys("key", "apiKey")
        if (fallback != null) next = next.copy(openAiKey = fallback)
    }
    if (!parsed.containsKey("translationEngine") && !parsed.containsKey("engine")) {
        next = when {
            parsed.keys.any { it in setOf("openAiKey", "openai_key", "openaiKey", "key", "apiKey") } ->
                next.copy(translationEngine = TranslationEngine.OPENAI)
            parsed.keys.any { it in setOf("googleApiKey", "google_api_key") } ->
                next.copy(translationEngine = TranslationEngine.GOOGLE)
            parsed.keys.any { it in setOf("deeplApiKey", "deepl_api_key", "deeplKey") } ->
                next.copy(translationEngine = TranslationEngine.DEEPL)
            parsed.keys.any { it in setOf("geminiApiKey", "gemini_api_key") } ->
                next.copy(translationEngine = TranslationEngine.GEMINI)
            else -> next
        }
    }
    return next.normalized()
}
