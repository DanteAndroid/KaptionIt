package com.danteandroid.transbee.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

object CodeSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Code", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = input.decodeJsonElement() as? JsonPrimitive
        return primitive?.contentOrNull ?: ""
    }
}

@Serializable
private data class MinerUBaseResponse<T>(
    @Serializable(with = CodeSerializer::class)
    val code: String,
    val msg: String,
    val data: T? = null
)

@Serializable
private data class MinerUBatchData(
    val batch_id: String,
    val file_urls: List<String>
)

@Serializable
private data class MinerUBatchStatus(
    val batch_id: String,
    val extract_result: List<ExtractResult> = emptyList()
)

@Serializable
private data class ExtractResult(
    val file_name: String = "",
    val state: String,
    val full_zip_url: String? = null,
    val err_msg: String? = null
)

class MinerUApiException(val code: String, override val message: String) : Exception(message)

private class CountingFileInputStream(
    file: File,
    private val totalBytes: Long,
    private val onProgress: (Long) -> Unit,
) : FileInputStream(file) {
    private var sent = 0L
    private var lastBucket = -1L

    private fun emit(sentNow: Long) {
        val bucket = sentNow / (256 * 1024)
        if (bucket != lastBucket || sentNow >= totalBytes) {
            lastBucket = bucket
            onProgress(sentNow)
        }
    }

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) {
            sent++
            emit(sent)
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) {
            sent += n.toLong()
            emit(sent)
        }
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) {
            sent += skipped
            emit(sent)
        }
        return skipped
    }
}

class MinerUClient(private val token: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://mineru.net/api/v4"

    /** 提交识别任务 (严格遵循 V4 官方示例) */
    suspend fun submit(file: File, onUploadProgress: ((Long, Long) -> Unit)? = null): String =
        withContext(Dispatchers.IO) {
            val totalLen = file.length().coerceAtLeast(0L)
            // 1. 申请上传地址
            // 增加 data_id 字段，model_version 设为 vlm
            val dataId = UUID.randomUUID().toString().take(8)
            val batchRequest = """
            {
                "files": [{"name": "${file.name}", "data_id": "$dataId"}],
                "model_version": "vlm"
            }
        """.trimIndent()

            val reqUrlRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/file-urls/batch"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .POST(HttpRequest.BodyPublishers.ofString(batchRequest))
                .build()

            val reqUrlRes =
                tryWithRetry { client.send(reqUrlRequest, HttpResponse.BodyHandlers.ofString()) }
                    ?: throw MinerUApiException("-1", "Network error visiting MinerU")

            if (reqUrlRes.statusCode() != 200) {
                throw MinerUApiException(
                    reqUrlRes.statusCode().toString(),
                    "Apply URL Failed: ${reqUrlRes.body()}"
                )
            }

            val batchData =
                json.decodeFromString<MinerUBaseResponse<MinerUBatchData>>(reqUrlRes.body()).let {
                    if (it.code != "0") throw MinerUApiException(it.code, it.msg)
                    it.data ?: throw MinerUApiException("-1", "Batch data missing")
                }

            val uploadUrl = batchData.file_urls.firstOrNull() ?: throw MinerUApiException(
                "-1",
                "Upload URL missing"
            )

            // 2. PUT 上传二进制文件 (注意：根据文档，此处不设置 Content-Type)
            onUploadProgress?.invoke(0L, totalLen)
            val uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(Duration.ofMinutes(20))
                .PUT(
                    HttpRequest.BodyPublishers.ofInputStream {
                        CountingFileInputStream(file, totalLen) { sent ->
                            onUploadProgress?.invoke(sent, totalLen)
                        }
                    },
                )
                .build()

            val uploadRes =
                tryWithRetry { client.send(uploadRequest, HttpResponse.BodyHandlers.ofString()) }
                    ?: throw MinerUApiException("-1", "Upload error")

            if (uploadRes.statusCode() !in 200..299) {
                throw MinerUApiException(
                    uploadRes.statusCode().toString(),
                    "OSS Upload Failed: ${uploadRes.body()}"
                )
            }

            batchData.batch_id
        }

    private suspend fun <T> tryWithRetry(maxRetries: Int = 3, block: suspend () -> T): T? {
        var lastEx: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastEx = e
                if (attempt < maxRetries - 1) {
                    val delayMs = (attempt + 1) * 2000L
                    delay(delayMs)
                }
            }
        }
        throw lastEx ?: Exception("Unknown error")
    }

    /** 轮询任务状态 */
    suspend fun poll(batchId: String, onState: (String) -> Unit): String {
        val pollUrl = "$baseUrl/extract-results/batch/$batchId"
        while (true) {
            val res = tryWithRetry {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(pollUrl))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer $token")
                    .GET().build()
                client.send(request, HttpResponse.BodyHandlers.ofString())
            } ?: throw MinerUApiException("-1", "Network error after retries")

            if (res.statusCode() != 200) {
                throw MinerUApiException(res.statusCode().toString(), "Poll Failed: ${res.body()}")
            }

            val parsed = json.decodeFromString<MinerUBaseResponse<MinerUBatchStatus>>(res.body())
            if (parsed.code != "0") throw MinerUApiException(parsed.code, parsed.msg)

            val status = parsed.data ?: throw MinerUApiException("-1", "Batch status empty")
            val result = status.extract_result.firstOrNull()

            if (result == null) {
                onState("waiting")
                delay(5000)
            } else when (result.state) {
                "done" -> return result.full_zip_url ?: throw MinerUApiException(
                    "-1",
                    "Zip URL missing"
                )

                "error", "failed" -> throw MinerUApiException(
                    "-1",
                    result.err_msg ?: "Cloud processing error"
                )

                else -> {
                    onState("")
                    delay(5000)
                }
            }
        }
    }
}
