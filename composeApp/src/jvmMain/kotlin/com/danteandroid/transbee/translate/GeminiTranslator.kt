package com.danteandroid.transbee.translate

import com.danteandroid.transbee.utils.JvmResourceStrings
import com.danteandroid.transbee.utils.TransbeeLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_gemini_http
import transbee.composeapp.generated.resources.err_openai_no_content
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets

class GeminiTranslator(
    apiKey: String,
    model: String,
    glossaryMappings: List<GlossaryLoader.TermMapping> = emptyList(),
    userPrompt: String = "",
    enforceSubtitleBatchRules: Boolean = true,
) : OpenAiTranslator(
    apiKey = apiKey,
    model = normalizeGeminiModelId(model),
    baseUrl = "https://api.openai.com/v1",
    glossaryMappings = glossaryMappings,
    userPrompt = userPrompt,
    enforceSubtitleBatchRules = enforceSubtitleBatchRules,
) {
    override fun postChatCompletion(
        texts: List<String>,
        targetLanguage: String,
        maxTokensOverride: Int?,
        contexts: List<LlmSubtitleContext>,
    ): LlmCompletionPayload {
        val (systemMsg, userMsg, maxTokens) = buildTranslationMessages(
            texts,
            targetLanguage,
            maxTokensOverride,
            contexts,
        )
        val body = json.encodeToString(
            GeminiGenerateRequest(
                systemInstruction = GeminiPartsWrapper(parts = listOf(GeminiTextPart(text = systemMsg))),
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiTextPart(text = userMsg)),
                    ),
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1,
                    maxOutputTokens = maxTokens,
                    responseMimeType = "application/json",
                ),
            ),
        )
        val keyParam = URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        val uri = URI.create(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$keyParam",
        )
        val request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Content-Type", "application/json; charset=utf-8")
            .timeout(TranslationHttp.requestTimeoutLlm)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = TranslationHttp.sendString(http, request)
        val responseBody = response.body()
        if (response.statusCode() !in 200..299) {
            val code = response.statusCode()
            TranslationHttp.ensureNotRateLimited(code)
            val snippet = responseBody.trim().take(500)
            error(JvmResourceStrings.text(Res.string.err_gemini_http, code, snippet))
        }

        TransbeeLog.llmHttp("Gemini/request") { body }
        TransbeeLog.llmHttp("Gemini/user") { userMsg }
        TransbeeLog.llmHttp("Gemini/response") { responseBody }

        val parsed = runCatching {
            json.decodeFromString(GeminiGenerateResponse.serializer(), responseBody)
        }.getOrNull()
        val blockReason = parsed?.promptFeedback?.blockReason
        if (blockReason != null) {
            error(JvmResourceStrings.text(Res.string.err_gemini_http, 400, "blocked: $blockReason"))
        }
        val candidate = parsed?.candidates?.firstOrNull()
        val text = candidate?.content?.parts?.firstOrNull()?.text?.trim().orEmpty()
        if (text.isEmpty()) {
            error(JvmResourceStrings.text(Res.string.err_openai_no_content))
        }
        val finish = candidate?.finishReason
        val mappedFinish = when {
            finish.equals("MAX_TOKENS", ignoreCase = true) -> "length"
            finish.equals("STOP", ignoreCase = true) || finish.isNullOrBlank() -> null
            else -> finish
        }
        return LlmCompletionPayload(content = text, finishReason = mappedFinish)
    }
}

private fun normalizeGeminiModelId(raw: String): String {
    var m = raw.trim().ifEmpty { "gemini-3.1-flash-lite-preview" }
    if (m.startsWith("models/", ignoreCase = true)) {
        m = m.substring("models/".length)
    }
    return m
}

@Serializable
private data class GeminiGenerateRequest(
    val systemInstruction: GeminiPartsWrapper? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
private data class GeminiPartsWrapper(
    val parts: List<GeminiTextPart>,
)

@Serializable
private data class GeminiContent(
    val role: String,
    val parts: List<GeminiTextPart>,
)

@Serializable
private data class GeminiTextPart(
    val text: String,
)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double = 0.1,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
    @SerialName("responseMimeType") val responseMimeType: String = "application/json",
)

@Serializable
private data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null,
)

@Serializable
private data class GeminiPromptFeedback(
    val blockReason: String? = null,
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiPartsWrapper? = null,
    val finishReason: String? = null,
)
