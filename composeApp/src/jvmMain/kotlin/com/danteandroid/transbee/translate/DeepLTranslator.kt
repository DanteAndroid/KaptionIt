package com.danteandroid.transbee.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration
import kotlin.math.min

class DeepLTranslator(
    private val authKey: String,
    private val useFreeApiHost: Boolean = true,
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val endpoint: String
        get() = if (useFreeApiHost) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }

    suspend fun translateBatch(texts: List<String>, targetDeepL: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            val out = ArrayList<String>(texts.size)
            var i = 0
            while (i < texts.size) {
                val single = texts[i]
                if (single.isNotEmpty() && exceedsSingleRequestBudget(single, targetDeepL)) {
                    out.add(translateLongText(single, targetDeepL))
                    i++
                    continue
                }
                var end = min(i + MAX_BATCH, texts.size)
                var batchSent = false
                while (end > i) {
                    val slice = texts.subList(i, end)
                    if (jsonBodyUtf8Bytes(slice, targetDeepL) > MAX_BODY_BYTES) {
                        end--
                        continue
                    }
                    val list = postTranslate(slice, targetDeepL)
                    list.forEach { out.add(it) }
                    i = end
                    batchSent = true
                    break
                }
                if (!batchSent && end == i) {
                    out.add(translateLongText(single, targetDeepL))
                    i++
                }
            }
            out
        }

    private fun exceedsSingleRequestBudget(text: String, targetDeepL: String): Boolean =
        text.length > MAX_CHARS_PER_TEXT ||
            jsonBodyUtf8Bytes(listOf(text), targetDeepL) > MAX_BODY_BYTES

    private suspend fun translateLongText(text: String, targetDeepL: String): String {
        val chunks = splitUtf16Chunks(text, MAX_CHUNK_CHARS)
        return buildString(chunks.sumOf { it.length }) {
            for (chunk in chunks) {
                append(postTranslate(listOf(chunk), targetDeepL).single())
            }
        }
    }

    private fun jsonBodyUtf8Bytes(texts: List<String>, targetDeepL: String): Int =
        json.encodeToString(
            DeepLRequest.serializer(),
            DeepLRequest(
                text = texts,
                targetLang = targetDeepL,
            ),
        ).toByteArray(Charsets.UTF_8).size

    private suspend fun postTranslate(slice: List<String>, targetDeepL: String): List<String> {
        val body = json.encodeToString(
            DeepLRequest.serializer(),
            DeepLRequest(
                text = slice,
                targetLang = targetDeepL,
            ),
        )
        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(TranslationHttp.requestTimeout)
            .header("Authorization", "DeepL-Auth-Key $authKey")
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = TranslationHttp.sendString(http, request)
        if (response.statusCode() !in 200..299) {
            val code = response.statusCode()
            TranslationHttp.ensureNotRateLimited(code)
            error("DeepL 服务返回错误（错误码 $code），请检查密钥或网络。")
        }
        val parsed = json.decodeFromString(DeepLResponse.serializer(), response.body())
        val list = parsed.translations
        if (list.size != slice.size) {
            error("翻译结果异常，请重试。")
        }
        return list.map { it.text }
    }

    private companion object {
        const val MAX_BATCH = 40
        const val MAX_BODY_BYTES = 120_000
        const val MAX_CHARS_PER_TEXT = 50_000
        const val MAX_CHUNK_CHARS = 8_000

        fun splitUtf16Chunks(s: String, maxChars: Int): List<String> {
            if (s.isEmpty()) return listOf("")
            if (s.length <= maxChars) return listOf(s)
            return buildList {
                var start = 0
                while (start < s.length) {
                    add(s.substring(start, min(start + maxChars, s.length)))
                    start += maxChars
                }
            }
        }
    }
}

@Serializable
private data class DeepLRequest(
    val text: List<String>,
    @SerialName("target_lang") val targetLang: String,
)

@Serializable
private data class DeepLResponse(
    val translations: List<DeepLItem> = emptyList(),
)

@Serializable
private data class DeepLItem(
    val text: String,
)
