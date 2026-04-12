package com.danteandroid.transbee.translate

import com.danteandroid.transbee.utils.JvmResourceStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_google_http
import transbee.composeapp.generated.resources.err_translate_result_invalid
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration

class GoogleTranslator(
    private val apiKey: String,
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun translateBatch(texts: List<String>, targetGoogleCode: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            val out = ArrayList<String>(texts.size)
            val chunkSize = 100
            for (chunk in texts.chunked(chunkSize)) {
                val body = json.encodeToString(
                    TranslateV2Request.serializer(),
                    TranslateV2Request(
                        q = chunk,
                        target = targetGoogleCode,
                        format = "text",
                    ),
                )
                val keyParam = URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                val uri = URI.create(
                    "https://translation.googleapis.com/language/translate/v2?key=$keyParam",
                )
                val request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TranslationHttp.requestTimeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
                val response = TranslationHttp.sendString(http, request)
                if (response.statusCode() !in 200..299) {
                    val code = response.statusCode()
                    TranslationHttp.ensureNotRateLimited(code)
                    error(JvmResourceStrings.text(Res.string.err_google_http, code))
                }
                val parsed = json.decodeFromString(TranslateV2Response.serializer(), response.body())
                val list = parsed.data?.translations.orEmpty()
                if (list.size != chunk.size) {
                    error(JvmResourceStrings.text(Res.string.err_translate_result_invalid))
                }
                list.forEach { out.add(it.translatedText) }
            }
            out
        }
}

@Serializable
private data class TranslateV2Request(
    val q: List<String>,
    val target: String,
    val format: String = "text",
)

@Serializable
private data class TranslateV2Response(
    val data: TranslateV2Data? = null,
)

@Serializable
private data class TranslateV2Data(
    val translations: List<TranslateV2Translation> = emptyList(),
)

@Serializable
private data class TranslateV2Translation(
    @SerialName("translatedText") val translatedText: String,
)
