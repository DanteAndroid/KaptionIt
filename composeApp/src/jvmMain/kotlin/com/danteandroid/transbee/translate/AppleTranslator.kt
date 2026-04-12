package com.danteandroid.transbee.translate

import com.danteandroid.transbee.native.BundledNativeTools
import com.danteandroid.transbee.utils.JvmResourceStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_apple_translate_failed_code
import transbee.composeapp.generated.resources.err_apple_translate_json_parse
import transbee.composeapp.generated.resources.err_apple_translate_missing
import transbee.composeapp.generated.resources.err_apple_translate_output_empty
import transbee.composeapp.generated.resources.err_apple_translate_pending
import transbee.composeapp.generated.resources.err_json_root_not_object
import transbee.composeapp.generated.resources.err_translate_failed_reason
import transbee.composeapp.generated.resources.err_translate_result_invalid
import transbee.composeapp.generated.resources.err_translation_no_response

class AppleTranslator(
    private val binaryPath: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun translateBatch(
        texts: List<String>,
        sourceAppleLocale: String,
        targetAppleLocale: String,
        timeoutMs: Long = DEFAULT_BATCH_TIMEOUT_MS,
    ): List<String> = withContext(Dispatchers.IO) {
        if (sourceAppleLocale.equals(targetAppleLocale, ignoreCase = true)) {
            return@withContext texts
        }
        val bin = File(binaryPath)
        if (!bin.isFile) {
            error(JvmResourceStrings.text(Res.string.err_apple_translate_missing))
        }
        val inFile = Files.createTempFile("transbee_apple_in_", ".json")
        val outFile = Files.createTempFile("transbee_apple_out_", ".json")
        try {
            val payload = json.encodeToString(
                AppleTranslateInput.serializer(),
                AppleTranslateInput(
                    texts = texts,
                    source = sourceAppleLocale,
                    target = targetAppleLocale,
                ),
            )
            Files.writeString(inFile, payload, StandardOpenOption.TRUNCATE_EXISTING)
            Files.writeString(
                outFile,
                """{"error":"pending","translations":null}""",
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE,
            )
            val pb = ProcessBuilder(
                binaryPath,
                inFile.toAbsolutePath().toString(),
                outFile.toAbsolutePath().toString(),
            )
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            val process = pb.start()
            val code = waitForProcessOrDestroy(process, timeoutMs)
            val outText = readAppleOutFile(outFile)
            val parsed = parseAppleTranslateOutput(outText, code)
            parsed.error?.let { err ->
                if (err == "pending") {
                    error(JvmResourceStrings.text(Res.string.err_apple_translate_pending, code))
                } else {
                    error(JvmResourceStrings.text(Res.string.err_translate_failed_reason, err))
                }
            }
            val tr = parsed.translations
            if (tr == null || tr.size != texts.size) {
                error(JvmResourceStrings.text(Res.string.err_translate_result_invalid))
            }
            if (code != 0) {
                error(JvmResourceStrings.text(Res.string.err_apple_translate_failed_code, code))
            }
            tr
        } finally {
            runCatching { Files.deleteIfExists(inFile) }
            runCatching { Files.deleteIfExists(outFile) }
        }
    }

    private fun readAppleOutFile(outFile: java.nio.file.Path): String {
        repeat(10) { attempt ->
            val s = runCatching { Files.readString(outFile) }.getOrDefault("")
            if (s.isNotBlank()) {
                return s
            }
            if (attempt < 9) {
                try {
                    Thread.sleep(80L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        return ""
    }

    private fun parseAppleTranslateOutput(raw: String, exitCode: Int): AppleTranslateOutput {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) {
            error(JvmResourceStrings.text(Res.string.err_apple_translate_output_empty, exitCode))
        }
        try {
            return json.decodeFromString(AppleTranslateOutput.serializer(), trimmed)
        } catch (e: Exception) {
            try {
                return parseAppleOutputLenient(trimmed)
            } catch (e2: Exception) {
                error(
                    JvmResourceStrings.text(
                        Res.string.err_apple_translate_json_parse,
                        e.message ?: e.toString(),
                        e2.message ?: e2.toString(),
                        trimmed.take(500),
                    ),
                )
            }
        }
    }

    private fun parseAppleOutputLenient(trimmed: String): AppleTranslateOutput {
        val root = json.parseToJsonElement(trimmed)
        val obj = root as? JsonObject
            ?: error(JvmResourceStrings.text(Res.string.err_json_root_not_object))
        val err = obj["error"]?.jsonPrimitive?.contentOrNull
            ?: obj["Error"]?.jsonPrimitive?.contentOrNull
        val arrEl = obj["translations"] ?: obj["Translations"]
        val list = when (arrEl) {
            null -> null
            is JsonArray -> arrEl.map { el ->
                when (el) {
                    is JsonPrimitive -> el.content
                    else -> el.toString().trim('"')
                }
            }
            else -> null
        }
        return AppleTranslateOutput(translations = list, error = err)
    }

    private fun waitForProcessOrDestroy(process: Process, timeoutMs: Long): Int {
        val exitHolder = IntArray(1) { -99999 }
        val waiter = Thread({
            try {
                exitHolder[0] = process.waitFor()
            } catch (_: InterruptedException) {
            }
        }, "transbee-apple-wait")
        waiter.isDaemon = true
        waiter.start()
        waiter.join(timeoutMs)
        if (waiter.isAlive) {
            process.destroyForcibly()
            waiter.interrupt()
            waiter.join(3000)
            error(JvmResourceStrings.text(Res.string.err_translation_no_response))
        }
        return exitHolder[0]
    }

    private companion object {
        private const val DEFAULT_BATCH_TIMEOUT_MS = 20_000L
    }
}

@Serializable
private data class AppleTranslateInput(
    val texts: List<String>,
    val source: String,
    val target: String,
)

@Serializable
private data class AppleTranslateOutput(
    val translations: List<String>? = null,
    val error: String? = null,
)

object AppleTranslateBinary {
    fun defaultSearchPaths(): List<File> {
        val home = System.getProperty("user.home") ?: return emptyList()
        return listOf(
            File(home, ".transbee/bin/AppleTranslate"),
            File(home, "Library/Application Support/transbee/bin/AppleTranslate"),
        )
    }

    private fun bundledFromPackagedApp(): File? {
        val dir = BundledNativeTools.bundledResourcesDirectory() ?: return null
        val f = File(dir, "AppleTranslate")
        if (!f.isFile) return null
        if (!f.canExecute()) {
            f.setExecutable(true, false)
        }
        return f
    }

    fun resolvePath(configured: String): String? {
        val c = configured.trim()
        if (c.isNotEmpty()) {
            val f = File(c)
            if (f.isFile && f.canExecute()) return f.absolutePath
            return null
        }
        System.getenv("APPLE_TRANSLATE_BINARY")?.trim()?.takeIf { it.isNotEmpty() }?.let { env ->
            val f = File(env)
            if (f.isFile && f.canExecute()) return f.absolutePath
        }
        bundledFromPackagedApp()?.let { return it.absolutePath }
        for (p in defaultSearchPaths()) {
            if (p.isFile && p.canExecute()) return p.absolutePath
        }
        return null
    }
}
