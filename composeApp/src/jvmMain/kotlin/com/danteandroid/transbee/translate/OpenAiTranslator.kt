package com.danteandroid.transbee.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration

class OpenAiTranslator(
    private val apiKey: String,
    private val model: String = "gpt-5-mini",
    private val baseUrl: String = "https://api.openai.com/v1",
    private val enforceSubtitleBatchRules: Boolean = true,
) {
    companion object {
        fun chatCompletionsEndpoint(baseUrl: String): String {
            val t = baseUrl.trim()
            if (t.contains("/chat/completions", ignoreCase = true)) return t
            return "${t.trimEnd('/')}/chat/completions"
        }
    }

    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun translateBatch(texts: List<String>, targetLanguage: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            val out = MutableList(texts.size) { texts[it] }
            val apiIndices = mutableListOf<Int>()
            val apiTexts = mutableListOf<String>()
            texts.forEachIndexed { i, t ->
                if (shouldSkipApiForLine(t)) {
                    out[i] = t.trim()
                } else {
                    apiIndices.add(i)
                    apiTexts.add(t)
                }
            }
            if (apiTexts.isEmpty()) return@withContext out
            val translated = translateBatchCore(apiTexts, targetLanguage)
            apiIndices.forEachIndexed { j, origIdx ->
                out[origIdx] = translated[j]
            }
            out
        }

    private fun shouldSkipApiForLine(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        return !t.any { ch -> Character.isLetter(ch) || Character.isDigit(ch) }
    }

    private data class CompletionPayload(
        val content: String,
        val finishReason: String?,
    )

    private fun translateBatchCore(texts: List<String>, targetLanguage: String): List<String> {
        if (!enforceSubtitleBatchRules) {
            return translateDocumentBatchWithSplit(texts, targetLanguage, depth = 0)
        }
        val payload = postChatCompletion(texts, targetLanguage, maxTokensOverride = null)
        return parseResponse(payload.content, texts, targetLanguage, enforceSubtitleBatchRules)
    }

    /**
     * 文档模式：输出 JSON 过长时易被 max_tokens 截断。监测 finish_reason=length 或解析失败时自动对半拆分重试；
     * 单条极长时尝试提高 max_tokens 再请求一次。
     */
    private fun translateDocumentBatchWithSplit(
        texts: List<String>,
        targetLanguage: String,
        depth: Int,
    ): List<String> {
        require(texts.isNotEmpty())
        if (depth > 14) {
            error("文档翻译拆分次数过多，请改小批大小或换模型。")
        }
        val payload = postChatCompletion(texts, targetLanguage, maxTokensOverride = null)
        val truncated = payload.finishReason.equals("length", ignoreCase = true)
        val parsed = runCatching {
            parseResponse(payload.content, texts, targetLanguage, strictSubtitle = false)
        }
        if (parsed.isSuccess && !truncated) return parsed.getOrThrow()

        if (texts.size == 1) {
            if (truncated || parsed.isFailure) {
                val payload2 = postChatCompletion(texts, targetLanguage, maxTokensOverride = 32768)
                val truncated2 = payload2.finishReason.equals("length", ignoreCase = true)
                val parsed2 = runCatching {
                    parseResponse(
                        payload2.content,
                        texts,
                        targetLanguage,
                        strictSubtitle = false,
                    )
                }
                if (parsed2.isSuccess && !truncated2) return parsed2.getOrThrow()
            }
            val cause = parsed.exceptionOrNull()
            val hint = buildString {
                append("翻译返回无法解析或已被截断（finish_reason=${payload.finishReason ?: "N/A"}，助手正文约 ${payload.content.length} 字符；以下为前 400 字符，非完整 output）。")
                append(payload.content.trim().take(400))
                if (cause != null) {
                    append(" | 解析异常：")
                    append(cause.message ?: cause.toString())
                }
            }
            throw IllegalStateException(hint)
        }

        val mid = texts.size / 2
        val first = translateDocumentBatchWithSplit(texts.subList(0, mid), targetLanguage, depth + 1)
        val second = translateDocumentBatchWithSplit(texts.subList(mid, texts.size), targetLanguage, depth + 1)
        return first + second
    }

    private fun postChatCompletion(
        texts: List<String>,
        targetLanguage: String,
        maxTokensOverride: Int?,
    ): CompletionPayload {
        val inputArr = json.encodeToString(
            texts.mapIndexed { idx, text -> IndexedLine(id = idx, text = text) },
        )

        val systemMsg = buildString {
            appendLine("你是一个严格的数据处理机器，只输出 JSON，不要有任何废话。")
            appendLine("你将收到一个包含字幕数组的 JSON。你需要将其翻译成「$targetLanguage」，并返回结构一模一样的 JSON。")
            appendLine("【绝对强制规则】：")
            appendLine("1. 输入了多少条记录，输出就必须是多少条！绝对禁止合并、删减或拆分任何条目。")
            appendLine("2. 即使原文只有语气词（如啊、嗯）、单独的符号，或者是残缺的句子，你也必须原样保留或简单意译，严禁跳过或忽略该条目。")
            appendLine("3. 返回格式必须为严格的 JSON 对象：{\"translations\":[{\"id\":0,\"text\":\"译文\"}, ...]}")
            appendLine("4. 'id' 必须与输入的 'id' 完全一一对应。")
            appendLine("5. 译文尽量简洁，不要扩写，避免输出超长导致 JSON 被截断。")
        }

        val userMsg = "输入文本：\n$inputArr"

        val maxTokens = maxTokensOverride ?: if (enforceSubtitleBatchRules) {
            4096
        } else {
            val estimated = texts.sumOf { it.length } * 2 + 8192
            estimated.coerceIn(12288, 16384)
        }

        val body = json.encodeToString(
            ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = systemMsg),
                    ChatMessage(role = "user", content = userMsg),
                ),
                temperature = 0.1,
                maxTokens = maxTokens,
                responseFormat = ResponseFormat(type = "json_object"),
            )
        )

        val endpoint = chatCompletionsEndpoint(baseUrl)
        val endpointUri = try {
            URI.create(endpoint)
        } catch (_: Exception) {
            error("服务商 API 地址格式无效：$endpoint")
        }
        val request = HttpRequest.newBuilder()
            .uri(endpointUri)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .timeout(TranslationHttp.requestTimeoutLlm)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        var http520Retries = 0
        var response = TranslationHttp.sendString(http, request)
        while (response.statusCode() == 520 && !enforceSubtitleBatchRules && http520Retries < 2) {
            http520Retries++
            Thread.sleep(500L * http520Retries)
            response = TranslationHttp.sendString(http, request)
        }

        if (response.statusCode() !in 200..299) {
            val code = response.statusCode()
            TranslationHttp.ensureNotRateLimited(code)
            val snippet = response.body().trim().take(500)
            val hint = if (code == 404) {
                "（404：地址请填到 …/v1 这类前缀，或填完整 …/chat/completions。）"
            } else ""
            error("OpenAI 接口 HTTP $code $hint $snippet".trim())
        }

        val completion = json.decodeFromString<ChatCompletionResponse>(response.body())
        val choice = completion.choices.firstOrNull()
        val content = choice?.message?.content
            ?: error("翻译没有返回内容，请重试。")
        return CompletionPayload(content = content, finishReason = choice.finishReason)
    }

    private fun parseResponse(
        content: String,
        sources: List<String>,
        targetLanguage: String,
        strictSubtitle: Boolean,
    ): List<String> {
        val expectedSize = sources.size
        val trimmed = content.trim()

        val rootObj = try {
            json.parseToJsonElement(trimmed) as? JsonObject
        } catch (_: Exception) {
            null
        }

        val arr = rootObj?.get("translations")?.jsonArray
            ?: tryExtractArray(trimmed)
            ?: error(
                "翻译返回无法解析为 JSON（末尾不完整多为输出被截断）。请确认接口兼容 chat/completions。以下为助手正文前 400 字符，非完整 output：${trimmed.take(400)}"
            )

        val result = MutableList(expectedSize) { sources[it] }
        val filledIds = mutableSetOf<Int>()
        arr.forEach { el ->
            if (el is JsonObject) {
                val id = el["id"]?.jsonPrimitive?.intOrNull
                    ?: el["i"]?.jsonPrimitive?.intOrNull
                val text = el["text"]?.jsonPrimitive?.contentOrNull
                    ?: el["t"]?.jsonPrimitive?.contentOrNull
                if (id != null && id in 0 until expectedSize && !text.isNullOrBlank()) {
                    result[id] = text
                    filledIds.add(id)
                }
            }
        }

        if (filledIds.isEmpty()) {
            if (expectedSize == 1 && arr.size > 0) {
                val first = arr[0]
                val text = if (first is JsonObject) {
                    first["text"]?.jsonPrimitive?.contentOrNull
                        ?: first["t"]?.jsonPrimitive?.contentOrNull
                } else {
                    first.jsonPrimitive.contentOrNull
                }
                if (!text.isNullOrBlank()) return listOf(text)
            }
            error(
                "翻译返回缺少有效的 id+text 结构，已拒绝结果。以下为助手正文前 400 字符：${trimmed.take(400)}"
            )
        }

        if (strictSubtitle && filledIds.size < expectedSize) {
            error("翻译返回条数不足（期望 $expectedSize，实际 ${filledIds.size}），已拒绝结果以触发重试。")
        }

        if (strictSubtitle) {
            validateQualityOrThrow(sources, result, targetLanguage)
        }
        return result
    }

    private fun validateQualityOrThrow(
        sources: List<String>,
        translations: List<String>,
        targetLanguage: String,
    ) {
        val targetLower = targetLanguage.lowercase()
        val cjkTarget = targetLower.contains("zh") ||
                targetLower.contains("chinese") ||
                targetLower.contains("中文") ||
                targetLower.contains("中") ||
                targetLower.contains("ja") ||
                targetLower.contains("japanese") ||
                targetLower.contains("日") ||
                targetLower.contains("ko") ||
                targetLower.contains("korean") ||
                targetLower.contains("韩")
        val latinRegex = Regex("[A-Za-z]")
        val normalizer = Regex("[\\s\\p{Punct}，。！？、；：：“”‘’（）【】《》]+")

        fun norm(s: String): String = s.lowercase().replace(normalizer, "")
        fun latinRatio(s: String): Double {
            if (s.isEmpty()) return 0.0
            val latinCount = s.count { it in 'a'..'z' || it in 'A'..'Z' }
            return latinCount.toDouble() / s.length.toDouble()
        }

        var unchangedOrUntranslated = 0
        var suspiciousMerged = 0
        for (i in sources.indices) {
            val src = sources[i].trim()
            val dst = translations.getOrNull(i).orEmpty().trim()
            if (src.isEmpty() || dst.isEmpty()) continue

            val srcHasLatin = latinRegex.containsMatchIn(src)
            if (src.length >= 12 && srcHasLatin) {
                val sameAfterNormalize = norm(src) == norm(dst)
                val looksStillLatin = cjkTarget && latinRatio(dst) >= 0.70
                if (sameAfterNormalize || looksStillLatin) {
                    unchangedOrUntranslated++
                }
            }

            if (src.length >= 12 && dst.length > src.length * 3 && dst.count { it == '\n' } >= 2) {
                suspiciousMerged++
            }
        }

        val unchangedThreshold = maxOf(2, sources.size / 4)
        if (unchangedOrUntranslated >= unchangedThreshold) {
            error("检测到较多疑似未翻译条目（$unchangedOrUntranslated/${sources.size}），已拒绝结果并触发重试。")
        }
        if (suspiciousMerged > 0) {
            error("检测到疑似合并后的超长译文，已拒绝结果并触发重试。")
        }
    }

    private fun tryExtractArray(raw: String): kotlinx.serialization.json.JsonArray? {
        val start = raw.indexOf('[')
        if (start < 0) return null

        // 优先查找完整的数组结构
        var i = start
        var depth = 0
        var inStr = false
        while (i < raw.length) {
            val c = raw[i]
            if (inStr) {
                if (c == '\\' && i + 1 < raw.length) i += 2
                else if (c == '"') inStr = false
            } else {
                if (c == '"') inStr = true
                else if (c == '[') depth++
                else if (c == ']') {
                    depth--
                    if (depth == 0) {
                        return try {
                            json.parseToJsonElement(raw.substring(start, i + 1)).jsonArray
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
            i++
        }

        // 如果未找到完整闭合，说明 JSON 可能被截断，尝试补齐并恢复
        // 这是一个启发式过程：补齐引号、足够的大括号和数组括号
        val repaired = buildString {
            append(raw.substring(start))
            if (inStr) append('"')
            repeat(depth + 2) {
                append('}')
                append(']')
            }
        }

        return try {
            val element = json.parseToJsonElement(repaired)
            if (element is kotlinx.serialization.json.JsonArray) element
            else (element as? JsonObject)?.get("translations")?.jsonArray
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
private data class IndexedLine(
    val id: Int,
    val text: String,
)

@Serializable
private data class ResponseFormat(
    val type: String,
)

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
)

@Serializable
private data class Choice(
    val message: ChatMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)