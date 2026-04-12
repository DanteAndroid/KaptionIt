package com.danteandroid.transbee.translate

import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.utils.JvmResourceStrings
import com.danteandroid.transbee.utils.TransbeeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_api_url_invalid
import transbee.composeapp.generated.resources.err_openai_count_mismatch
import transbee.composeapp.generated.resources.err_openai_doc_split_too_many
import transbee.composeapp.generated.resources.err_openai_http
import transbee.composeapp.generated.resources.err_openai_merged_long
import transbee.composeapp.generated.resources.err_openai_no_content
import transbee.composeapp.generated.resources.err_openai_too_many_unchanged
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration

data class OpenAiBatchTranslateResult(
    val texts: List<String>,
    /** 与 texts 等长；API JSON 字段名为 `nc`，仅在字幕模式且配置了专业词库时由模型填充，否则为 false */
    val nc: List<Boolean>,
)

data class LlmSubtitleContext(
    val prev2: String? = null,
    val prev: String? = null,
    val next: String? = null,
    val next2: String? = null,
)

data class LlmCompletionPayload(
    val content: String,
    val finishReason: String?,
)

@Serializable
data class IndexedSubtitleLine(
    val id: Int,
    val text: String,
    val prev2: String? = null,
    val prev: String? = null,
    val next: String? = null,
    val next2: String? = null,
)

open class OpenAiTranslator(
    protected val apiKey: String,
    protected val model: String = "gpt-5-mini",
    private val baseUrl: String = "https://api.openai.com/v1",
    protected val enforceSubtitleBatchRules: Boolean = true,
    protected val glossaryMappings: List<GlossaryLoader.TermMapping> = emptyList(),
    protected val userPrompt: String = "",
) {
    private val glossaryPromptBlock: String = GlossaryLoader.toForcedTranslationPromptBlock(glossaryMappings)

    private val requestNeedCorrect: Boolean =
        enforceSubtitleBatchRules && glossaryMappings.isNotEmpty()

    companion object {
        fun chatCompletionsEndpoint(baseUrl: String): String {
            val t = baseUrl.trim()
            if (t.contains("/chat/completions", ignoreCase = true)) return t
            return "${t.trimEnd('/')}/chat/completions"
        }

        /** 与字幕翻译请求中的 `system` 正文一致（基于当前 [ToolingSettings]）。 */
        fun subtitleSystemPromptForSettings(cfg: ToolingSettings): String {
            val c = cfg.normalized()
            val forced = GlossaryLoader.normalizeMappings(
                c.forcedTranslationTerms.map { GlossaryLoader.TermMapping(source = it.source, target = it.target) },
            )
            return OpenAiTranslator(
                apiKey = "",
                glossaryMappings = forced,
                userPrompt = c.translationPrompt,
                enforceSubtitleBatchRules = true,
            ).buildSystemMessage(c.targetLanguage)
        }
    }

    protected fun buildSystemMessage(targetLanguage: String): String = buildString {
        if (userPrompt.isNotBlank()) {
            appendLine(userPrompt)
            appendLine()
        }
        appendLine("你是一个严格的数据处理机器，只输出 JSON，不要有任何废话。")
        if (enforceSubtitleBatchRules) {
            appendLine("你将收到一个包含字幕数组的 JSON。你需要将其翻译成「$targetLanguage」，并返回结构一模一样的 JSON。")
        } else {
            appendLine("你将收到一个待翻译文本片段数组的 JSON。你需要将其翻译成「$targetLanguage」，并返回结构一模一样的 JSON。")
        }
        appendLine("【绝对强制规则】：")
        appendLine("1. 输入了多少条记录，输出就必须是多少条！绝对禁止合并、删减或拆分任何条目。")
        appendLine("2. 即使原文只有语气词（如啊、嗯）、单独的符号，或者是残缺的句子，你也必须原样保留或简单意译，严禁跳过或忽略该条目。")
        if (requestNeedCorrect) {
            appendLine(
                "3. 返回格式必须为严格的 JSON 对象：{\"translations\":[{\"id\":0,\"text\":\"译文\",\"nc\":false}, ...]}。" +
                    "每条须含 id、text、nc（布尔）；对照后文【专业词汇】中的英文 source，" +
                    "若当前句疑似语音识别/听写错误且可能与某条 source 对应则 nc 为 true，否则为 false。不要滥用。",
            )
        } else {
            appendLine("3. 返回格式必须为严格的 JSON 对象：{\"translations\":[{\"id\":0,\"text\":\"译文\"}, ...]}。每条只需 id 与 text，不要输出 nc 及其他多余字段。")
        }
        appendLine("4. 'id' 必须与输入的 'id' 完全一一对应。")
        appendLine("5. 译文尽量简洁，不要扩写，避免输出超长导致 JSON 被截断。")
        appendLine("6. 输入里可能包含形如 ⟦P0⟧ 的占位符：它们代表 URL/数字/代码等不可改内容。你必须原样保留这些占位符，禁止翻译、改写、加空格或删除。")
        appendLine("7. 每条记录可能包含 prev/prev2/next/next2 字段（前后文），仅用于理解语境；你只翻译 text 字段，其余字段不需要输出。")
        if (enforceSubtitleBatchRules) {
            appendLine("8. 这是视频/音频字幕翻译。译文应自然口语化，贴近母语者的日常表达习惯，不要书面腔、不要机翻感。注意根据上下文推断代词指代和语气。")
        } else {
            appendLine("8. 请保持原文的格式结构（标题层级、列表标记、粗体斜体等 Markdown 语法），只翻译文字内容。")
        }
        if (glossaryPromptBlock.isNotEmpty()) {
            appendLine(glossaryPromptBlock)
        }
    }

    protected val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    protected fun buildTranslationMessages(
        texts: List<String>,
        targetLanguage: String,
        maxTokensOverride: Int?,
        contexts: List<LlmSubtitleContext> = emptyList(),
    ): Triple<String, String, Int> {
        val inputArr = json.encodeToString(
            texts.mapIndexed { idx, text ->
                val ctx = contexts.getOrNull(idx)
                IndexedSubtitleLine(
                    id = idx,
                    text = text,
                    prev2 = ctx?.prev2 ?: texts.getOrNull(idx - 2),
                    prev = ctx?.prev ?: texts.getOrNull(idx - 1),
                    next = ctx?.next ?: texts.getOrNull(idx + 1),
                    next2 = ctx?.next2 ?: texts.getOrNull(idx + 2),
                )
            },
        )
        val systemMsg = buildSystemMessage(targetLanguage)
        val userMsg = "输入文本：\n$inputArr"
        val maxTokens = maxTokensOverride ?: if (enforceSubtitleBatchRules) {
            4096
        } else {
            val estimated = texts.sumOf { it.length } * 2 + 8192
            estimated.coerceIn(12288, 16384)
        }
        return Triple(systemMsg, userMsg, maxTokens)
    }

    private data class ProtectedText(
        val text: String,
        val placeholders: Map<String, String>,
    )

    /** 字幕模式：保护 URL、邮箱、路径、行内代码、数字+单位 */
    private fun protectForSubtitle(text: String): ProtectedText = protectCommon(text)

    /** 文档模式：保护 URL、邮箱、行内代码、LaTeX 公式、Markdown 链接 */
    private fun protectForDocument(text: String): ProtectedText = protectCommon(text, docMode = true)

    private fun protectCommon(text: String, docMode: Boolean = false): ProtectedText {
        var s = text
        val map = linkedMapOf<String, String>()
        var idx = 0

        fun protectRegex(regex: Regex) {
            s = regex.replace(s) { m ->
                val raw = m.value
                val key = "\u27E6P${idx++}\u27E7"
                map[key] = raw
                key
            }
        }

        // 通用：URL / Email / 行内代码 / 数字+单位
        protectRegex(Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE))
        protectRegex(Regex("""\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b"""))
        protectRegex(Regex("""`[^`]+`"""))
        protectRegex(Regex("""\b\d[\d,._]*\s?(?:%|ms|s|sec|min|h|hz|khz|mhz|ghz|kb|mb|gb|tb|fps|dpi|\u2103|\u00B0c|\u00B0f)?\b""", RegexOption.IGNORE_CASE))

        if (docMode) {
            // Markdown 链接 [text](url)
            protectRegex(Regex("""\[([^\]]*)\]\(([^)]+)\)"""))
            // LaTeX 行间/行内公式
            protectRegex(Regex("""\$\$[^$]+\$\$"""))
            protectRegex(Regex("""\$[^$]+\$"""))
        } else {
            // 字幕专属：文件路径
            protectRegex(Regex("""(?:(?:[A-Za-z]:\\|/)[^\s]+)"""))
        }

        return ProtectedText(text = s, placeholders = map)
    }

    private fun unprotect(protected: ProtectedText, translated: String): String {
        if (protected.placeholders.isEmpty()) return translated
        var s = translated
        protected.placeholders.forEach { (k, v) ->
            s = s.replace(k, v)
        }
        return s
    }

    private fun normalizeSubtitleOutput(text: String): String {
        var s = text.trim()
        if (s.isEmpty()) return s
        // 轻量空白/省略号规范：不做激进的中英标点转换，避免误伤
        s = s.replace(Regex("""[ \t]{2,}"""), " ")
        s = s.replace("......", "\u2026")
        s = s.replace(".....", "\u2026")
        s = s.replace("....", "\u2026")
        s = s.replace("...", "\u2026")
        s = s.replace("..", "\u2026")
        return s
    }

    private fun extractNumberTokens(text: String): Set<String> {
        val raw = text.trim()
        if (raw.isEmpty()) return emptySet()
        val regex = Regex("""\d[\d,._]*""")
        return regex.findAll(raw).map { m ->
            m.value.replace(",", "").replace("_", "").trimEnd('.')
        }.filter { it.isNotEmpty() }.toSet()
    }

    private fun hasUnrestoredPlaceholders(text: String): Boolean =
        "\u27E6P" in text && "\u27E7" in text

    suspend fun translateBatch(texts: List<String>, targetLanguage: String): OpenAiBatchTranslateResult =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext OpenAiBatchTranslateResult(emptyList(), emptyList())
            val out = MutableList(texts.size) { texts[it] }
            val outNc = MutableList(texts.size) { false }
            val apiIndices = mutableListOf<Int>()
            val apiTexts = mutableListOf<ProtectedText>()
            // 在去重前基于原始顺序保存 +-2 句上下文
            val apiContexts = mutableListOf<LlmSubtitleContext>()
            texts.forEachIndexed { i, t ->
                if (shouldSkipApiForLine(t)) {
                    out[i] = t.trim()
                } else {
                    val protected = if (enforceSubtitleBatchRules) {
                        protectForSubtitle(t)
                    } else {
                        protectForDocument(t)
                    }
                    apiIndices.add(i)
                    apiTexts.add(protected)
                    apiContexts.add(
                        LlmSubtitleContext(
                            prev2 = texts.getOrNull(i - 2),
                            prev = texts.getOrNull(i - 1),
                            next = texts.getOrNull(i + 1),
                            next2 = texts.getOrNull(i + 2),
                        ),
                    )
                }
            }
            if (apiTexts.isEmpty()) {
                return@withContext OpenAiBatchTranslateResult(out, outNc)
            }
            // 同批去重：相同输入只翻一次，同时保留首次出现的上下文
            val uniqueOrder = LinkedHashMap<String, MutableList<Int>>()
            val uniqueContexts = LinkedHashMap<String, LlmSubtitleContext>()
            apiTexts.forEachIndexed { j, p ->
                uniqueOrder.getOrPut(p.text) { mutableListOf() }.add(j)
                if (p.text !in uniqueContexts) uniqueContexts[p.text] = apiContexts[j]
            }
            val uniqueTexts = uniqueOrder.keys.toList()
            val uniqueCtxList = uniqueTexts.map { uniqueContexts[it]!! }

            val parsedUnique = translateBatchCore(uniqueTexts, targetLanguage, uniqueCtxList)

            // 先填回（未做 QA/重试）
            val translatedProtected = MutableList(apiTexts.size) { apiTexts[it].text }
            val ncProtected = MutableList(apiTexts.size) { false }
            uniqueTexts.forEachIndexed { k, key ->
                val valText = parsedUnique.texts.getOrElse(k) { key }
                val valNc = parsedUnique.nc.getOrElse(k) { false }
                uniqueOrder[key]?.forEach { j ->
                    translatedProtected[j] = valText
                    ncProtected[j] = valNc
                }
            }

            // 本地 QA：数字一致性 + 占位符残留；仅对少量异常做一次重试
            if (enforceSubtitleBatchRules) {
                val retryIndices = mutableListOf<Int>()
                apiTexts.forEachIndexed { j, p ->
                    val src = p.text
                    val candidate = translatedProtected[j]
                    val srcNums = extractNumberTokens(src)
                    val dstNums = extractNumberTokens(candidate)
                    val badNumbers = srcNums.isNotEmpty() && srcNums != dstNums
                    val badPlaceholder = hasUnrestoredPlaceholders(candidate)
                    val badBlank = candidate.trim().isEmpty()
                    if (badBlank || badPlaceholder || badNumbers) retryIndices.add(j)
                }

                // 控制重试成本：只重试少量异常条目
                val capped = retryIndices.distinct().take(4)
                if (capped.isNotEmpty()) {
                    val retryTexts = capped.map { apiTexts[it].text }
                    val retryCtxs = capped.map { apiContexts[it] }
                    val retryParsed = translateBatchCore(retryTexts, targetLanguage, retryCtxs)
                    capped.forEachIndexed { r, j ->
                        translatedProtected[j] = retryParsed.texts.getOrElse(r) { translatedProtected[j] }
                        ncProtected[j] = retryParsed.nc.getOrElse(r) { ncProtected[j] }
                    }
                }
            }

            apiIndices.forEachIndexed { j, origIdx ->
                val p = apiTexts[j]
                val translated = unprotect(p, translatedProtected[j])
                val normalized = normalizeSubtitleOutput(translated)
                out[origIdx] = normalized
                outNc[origIdx] = ncProtected[j]
            }
            OpenAiBatchTranslateResult(out, outNc)
        }

    private fun shouldSkipApiForLine(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        return !t.any { ch -> Character.isLetter(ch) || Character.isDigit(ch) }
    }

    private data class ParsedSubtitleBatch(
        val texts: List<String>,
        val nc: List<Boolean>,
    )

    private fun translateBatchCore(
        texts: List<String>,
        targetLanguage: String,
        contexts: List<LlmSubtitleContext> = emptyList(),
    ): ParsedSubtitleBatch {
        if (!enforceSubtitleBatchRules) {
            return translateDocumentBatchWithSplit(texts, targetLanguage, depth = 0, contexts = contexts)
        }
        val payload = postChatCompletion(texts, targetLanguage, maxTokensOverride = null, contexts = contexts)
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
        contexts: List<LlmSubtitleContext> = emptyList(),
    ): ParsedSubtitleBatch {
        require(texts.isNotEmpty())
        if (depth > 14) {
            error(JvmResourceStrings.text(Res.string.err_openai_doc_split_too_many))
        }
        val payload = postChatCompletion(texts, targetLanguage, maxTokensOverride = null, contexts = contexts)
        val truncated = payload.finishReason.equals("length", ignoreCase = true)
        val parsed = runCatching {
            parseResponse(payload.content, texts, targetLanguage, strictSubtitle = false)
        }
        if (parsed.isSuccess && !truncated) return parsed.getOrThrow()

        if (texts.size == 1) {
            if (truncated || parsed.isFailure) {
                val payload2 = postChatCompletion(texts, targetLanguage, maxTokensOverride = 32768, contexts = contexts)
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
        val ctxFirst = if (contexts.size >= mid) contexts.subList(0, mid) else emptyList()
        val ctxSecond = if (contexts.size >= texts.size) contexts.subList(mid, texts.size) else emptyList()
        val first = translateDocumentBatchWithSplit(texts.subList(0, mid), targetLanguage, depth + 1, ctxFirst)
        val second = translateDocumentBatchWithSplit(texts.subList(mid, texts.size), targetLanguage, depth + 1, ctxSecond)
        return ParsedSubtitleBatch(
            texts = first.texts + second.texts,
            nc = first.nc + second.nc,
        )
    }

    protected open fun postChatCompletion(
        texts: List<String>,
        targetLanguage: String,
        maxTokensOverride: Int?,
        contexts: List<LlmSubtitleContext> = emptyList(),
    ): LlmCompletionPayload {
        val (systemMsg, userMsg, maxTokens) = buildTranslationMessages(
            texts,
            targetLanguage,
            maxTokensOverride,
            contexts,
        )

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
            error(JvmResourceStrings.text(Res.string.err_api_url_invalid, endpoint))
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

        val responseBody = response.body()

        if (response.statusCode() !in 200..299) {
            val code = response.statusCode()
            TranslationHttp.ensureNotRateLimited(code)
            val snippet = responseBody.trim().take(500)
            val hint = if (code == 404) {
                "（404：地址请填到 …/v1 这类前缀，或填完整 …/chat/completions。）"
            } else ""
            val detail = (hint + " " + snippet).trim()
            error(JvmResourceStrings.text(Res.string.err_openai_http, code, detail))
        }

        TransbeeLog.llmHttp("OpenAI/request") { body }
        TransbeeLog.llmHttp("OpenAI/user") { userMsg }
        TransbeeLog.llmHttp("OpenAI/response") { responseBody }

        val completion = json.decodeFromString<ChatCompletionResponse>(responseBody)
        val choice = completion.choices.firstOrNull()
        val content = choice?.message?.content
            ?: error(JvmResourceStrings.text(Res.string.err_openai_no_content))
        return LlmCompletionPayload(content = content, finishReason = choice.finishReason)
    }

    private fun parseResponse(
        content: String,
        sources: List<String>,
        targetLanguage: String,
        strictSubtitle: Boolean,
    ): ParsedSubtitleBatch {
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
        val ncFlags = MutableList(expectedSize) { false }
        val filledIds = mutableSetOf<Int>()
        arr.forEach { el ->
            if (el is JsonObject) {
                val id = el["id"]?.jsonPrimitive?.intOrNull
                    ?: el["i"]?.jsonPrimitive?.intOrNull
                val text = el["text"]?.jsonPrimitive?.contentOrNull
                    ?: el["t"]?.jsonPrimitive?.contentOrNull
                if (id != null && id in 0 until expectedSize && !text.isNullOrBlank()) {
                    result[id] = text
                    if (requestNeedCorrect && strictSubtitle) {
                        ncFlags[id] = el.readNcField()
                    }
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
                if (!text.isNullOrBlank()) {
                    val nc = if (first is JsonObject && requestNeedCorrect && strictSubtitle) {
                        first.readNcField()
                    } else {
                        false
                    }
                    return ParsedSubtitleBatch(listOf(text), listOf(nc))
                }
            }
            error(
                "翻译返回缺少有效的 id+text 结构，已拒绝结果。以下为助手正文前 400 字符：${trimmed.take(400)}"
            )
        }

        if (strictSubtitle && filledIds.size < expectedSize) {
            error(JvmResourceStrings.text(Res.string.err_openai_count_mismatch, expectedSize, filledIds.size))
        }

        if (strictSubtitle) {
            validateQualityOrThrow(sources, result, targetLanguage)
        }
        return ParsedSubtitleBatch(result, ncFlags)
    }

    private fun JsonObject.readNcField(): Boolean {
        val p = this["nc"]?.jsonPrimitive ?: return false
        p.booleanOrNull?.let { return it }
        p.intOrNull?.let { return it == 1 }
        val c = p.contentOrNull ?: return false
        return c.equals("true", ignoreCase = true)
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
        val normalizer = Regex("[\\s\\p{Punct}，。！？、；：：\u201C\u201D\u2018\u2019（）【】《》]+")

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
            error(JvmResourceStrings.text(Res.string.err_openai_too_many_unchanged, unchangedOrUntranslated, sources.size))
        }
        if (suspiciousMerged > 0) {
            error(JvmResourceStrings.text(Res.string.err_openai_merged_long))
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