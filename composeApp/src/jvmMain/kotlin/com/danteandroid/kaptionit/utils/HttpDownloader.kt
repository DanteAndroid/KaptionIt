package com.danteandroid.kaptionit.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration

object HttpDownloader {

    private val sharedClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    suspend fun downloadFile(
        url: String,
        dest: File,
        userAgent: String = "Whisperit/1.0 (compatible; Downloader)",
        timeout: Duration = Duration.ofMinutes(15),
        onProgress: (received: Long, total: Long?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (dest.isFile && dest.length() > 0L) {
            return@withContext dest
        }
        dest.parentFile?.mkdirs()
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
            error("文件下载失败（HTTP $code）")
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
        dest
    }
}
