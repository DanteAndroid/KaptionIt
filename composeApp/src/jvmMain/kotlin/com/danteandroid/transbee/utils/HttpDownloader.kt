package com.danteandroid.transbee.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_download_failed
import transbee.composeapp.generated.resources.err_download_http
import transbee.composeapp.generated.resources.log_network_io_retry
import transbee.composeapp.generated.resources.log_ssl_handshake_retry
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import javax.net.ssl.SSLContext

object HttpDownloader {

    private val sharedClient: HttpClient by lazy {
        val sslContext = SSLContext.getInstance("TLSv1.3").apply {
            init(null, null, null)
        }
        HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    /** 带重试的文件下载，默认最多重试 3 次 */
    suspend fun downloadFile(
        url: String,
        dest: File,
        userAgent: String = "Whisperit/1.0 (compatible; Downloader)",
        timeout: Duration = Duration.ofMinutes(15),
        maxRetries: Int = 3,
        onProgress: (received: Long, total: Long?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (dest.isFile && dest.length() > 0L) {
            return@withContext dest
        }
        dest.parentFile?.mkdirs()

        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return@withContext doDownload(url, dest, userAgent, timeout, onProgress)
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                lastException = e
                val delaySec = (attempt + 1) * 2L
                TransbeeLog.verbose {
                    JvmResourceStrings.text(
                        Res.string.log_ssl_handshake_retry,
                        attempt + 1,
                        delaySec,
                        e.message.orEmpty(),
                    )
                }
                delay(delaySec * 1000)
            } catch (e: java.io.IOException) {
                lastException = e
                val delaySec = (attempt + 1) * 2L
                TransbeeLog.verbose {
                    JvmResourceStrings.text(
                        Res.string.log_network_io_retry,
                        attempt + 1,
                        delaySec,
                        e.message.orEmpty(),
                    )
                }
                delay(delaySec * 1000)
            }
        }
        throw lastException ?: error(JvmResourceStrings.text(Res.string.err_download_failed))
    }

    private fun doDownload(
        url: String,
        dest: File,
        userAgent: String,
        timeout: Duration,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): File {
        onProgress(0L, null)
        val part = File(dest.parentFile, "${dest.name}.part")
        part.delete()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .GET()
            .build()

        val response = try {
            sharedClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: Exception) {
            runCatching { part.delete() }
            throw e
        }

        val code = response.statusCode()
        if (code !in 200..299) {
            runCatching { part.delete() }
            runCatching { response.body()?.close() }
            error(JvmResourceStrings.text(Res.string.err_download_http, code))
        }

        val contentLength = response.headers().firstValue("Content-Length")
            .map { it.toLong() }
            .orElse(null)

        try {
            response.body().use { input ->
                Files.newOutputStream(
                    part.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        received += n
                        onProgress(received, contentLength)
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { part.delete() }
            throw e
        }

        Files.move(
            part.toPath(),
            dest.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
        return dest
    }
}
