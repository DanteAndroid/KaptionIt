package com.danteandroid.transbee.translate

import com.danteandroid.transbee.utils.JvmResourceStrings
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_translation_no_response
import transbee.composeapp.generated.resources.err_translation_rate_limited
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

internal object TranslationHttp {
    val requestTimeout: Duration = Duration.ofSeconds(90)
    val requestTimeoutLlm: Duration = Duration.ofSeconds(120)

    fun sendString(client: HttpClient, request: HttpRequest): HttpResponse<String> =
        try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: HttpTimeoutException) {
            error(JvmResourceStrings.text(Res.string.err_translation_no_response))
        } catch (_: java.net.SocketTimeoutException) {
            error(JvmResourceStrings.text(Res.string.err_translation_no_response))
        }

    fun ensureNotRateLimited(statusCode: Int) {
        if (statusCode == 429 || statusCode == 456) {
            error(JvmResourceStrings.text(Res.string.err_translation_rate_limited))
        }
    }
}
