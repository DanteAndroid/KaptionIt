package com.danteandroid.kaptionit.translate

import com.danteandroid.kaptionit.settings.ToolingSettings
import com.danteandroid.kaptionit.utils.JvmResourceStrings
import com.danteandroid.kaptionit.utils.OsUtils
import com.danteandroid.kaptionit.whisper.TranscriptSegment
import com.danteandroid.kaptionit.whisper.WhisperParseResult
import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.err_apple_binary
import kaptionit.composeapp.generated.resources.err_apple_translate_macos_only
import kaptionit.composeapp.generated.resources.hint_apple_translate_pair
import kaptionit.composeapp.generated.resources.msg_translate_apple_progress
import kaptionit.composeapp.generated.resources.msg_translate_apple_running
import kaptionit.composeapp.generated.resources.msg_translate_deepl_progress
import kaptionit.composeapp.generated.resources.msg_translate_deepl_running
import kaptionit.composeapp.generated.resources.msg_translate_google_progress
import kaptionit.composeapp.generated.resources.msg_translate_google_running
import kaptionit.composeapp.generated.resources.msg_translate_openai_progress
import kaptionit.composeapp.generated.resources.msg_translate_openai_running
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/** OpenAI / DeepL 等按批翻译时的每批条数（降低单次请求上下文压力） */
private const val TranslationChunkSizeOpenAiStyle = 15

/** 多批并行翻译；与较小分片搭配，减轻末尾块排队（Google / DeepL） */
private const val TranslationConcurrency = 12

/** 自定义大模型（中转）对并发敏感，过高易 520；单独限流降至 2 求稳 */
private const val TranslationConcurrencyOpenAi = 2

class TranslationMetrics {
    val requestCount = AtomicInteger(0)
    val retryCount = AtomicInteger(0)
}

object TranslationService {

    suspend fun translateSegments(
        cfg: ToolingSettings,
        whisperDoc: WhisperParseResult,
        segments: List<TranscriptSegment>,
        metrics: TranslationMetrics,
        onProgressUpdate: (progress: Float, message: String) -> Unit
    ): List<String> {
        val engine = cfg.translationEngine
        val appleSource = TargetLanguageMapper.whisperLanguageToAppleSource(whisperDoc.whisperLanguage)
        val appleTarget = TargetLanguageMapper.toAppleLocale(cfg.targetLanguage, forTarget = true)
        val googleTarget = TargetLanguageMapper.toGoogleTargetCode(cfg.targetLanguage)
        val deeplTarget = TargetLanguageMapper.toDeepLTargetCode(cfg.targetLanguage)
        
        onProgressUpdate(
            0.65f,
            when (engine) {
                TranslationEngine.APPLE -> JvmResourceStrings.text(Res.string.msg_translate_apple_running)
                TranslationEngine.GOOGLE -> JvmResourceStrings.text(Res.string.msg_translate_google_running)
                TranslationEngine.DEEPL -> JvmResourceStrings.text(Res.string.msg_translate_deepl_running)
                TranslationEngine.OPENAI -> JvmResourceStrings.text(Res.string.msg_translate_openai_running)
            }
        )

        return when (engine) {
            TranslationEngine.APPLE -> {
                if (!OsUtils.isMacOs()) {
                    error(JvmResourceStrings.text(Res.string.err_apple_translate_macos_only))
                }
                val appleBin = AppleTranslateBinary.resolvePath(cfg.appleTranslateBinary)
                    ?: error(JvmResourceStrings.text(Res.string.err_apple_binary))
                val translator = AppleTranslator(binaryPath = appleBin)
                translateByChunk(
                    segments = segments,
                    chunkSize = 6,
                    metrics = metrics,
                    translateChunk = { texts ->
                        try {
                            translator.translateBatch(
                                texts = texts,
                                sourceAppleLocale = appleSource,
                                targetAppleLocale = appleTarget,
                            )
                        } catch (e: Exception) {
                            val hint = JvmResourceStrings.text(
                                Res.string.hint_apple_translate_pair,
                                appleSource,
                                appleTarget,
                                whisperDoc.whisperLanguage ?: "?",
                            )
                            error((e.message ?: e.toString()) + "\n" + hint)
                        }
                    },
                    progressMessage = { done, total ->
                        JvmResourceStrings.text(Res.string.msg_translate_apple_progress, done, total)
                    },
                    onProgressUpdate = onProgressUpdate
                )
            }

            TranslationEngine.GOOGLE -> {
                val translator = GoogleTranslator(apiKey = cfg.googleApiKey)
                translateByChunk(
                    segments = segments,
                    chunkSize = 100,
                    metrics = metrics,
                    translateChunk = { texts ->
                        translator.translateBatch(
                            texts = texts,
                            targetGoogleCode = googleTarget,
                        )
                    },
                    progressMessage = { done, total ->
                        JvmResourceStrings.text(Res.string.msg_translate_google_progress, done, total)
                    },
                    concurrency = TranslationConcurrency,
                    onProgressUpdate = onProgressUpdate
                )
            }

            TranslationEngine.DEEPL -> {
                val translator = DeepLTranslator(
                    authKey = cfg.deeplApiKey,
                    useFreeApiHost = cfg.deeplUseFreeApi,
                )
                translateByChunk(
                    segments = segments,
                    chunkSize = TranslationChunkSizeOpenAiStyle,
                    metrics = metrics,
                    translateChunk = { texts ->
                        translator.translateBatch(
                            texts = texts,
                            targetDeepL = deeplTarget,
                        )
                    },
                    progressMessage = { done, total ->
                        JvmResourceStrings.text(Res.string.msg_translate_deepl_progress, done, total)
                    },
                    concurrency = TranslationConcurrency,
                    onProgressUpdate = onProgressUpdate
                )
            }

            TranslationEngine.OPENAI -> {
                val translator = OpenAiTranslator(
                    apiKey = cfg.openAiKey,
                    model = cfg.openAiModel,
                    baseUrl = cfg.openAiBaseUrl,
                )
                translateByChunk(
                    segments = segments,
                    chunkSize = TranslationChunkSizeOpenAiStyle,
                    metrics = metrics,
                    translateChunk = { texts ->
                        translator.translateBatch(
                            texts = texts,
                            targetLanguage = cfg.targetLanguage,
                        )
                    },
                    progressMessage = { done, total ->
                        JvmResourceStrings.text(Res.string.msg_translate_openai_progress, done, total)
                    },
                    concurrency = TranslationConcurrencyOpenAi,
                    onProgressUpdate = onProgressUpdate
                )
            }
        }
    }

    private suspend fun translateByChunk(
        segments: List<TranscriptSegment>,
        chunkSize: Int,
        metrics: TranslationMetrics,
        translateChunk: suspend (texts: List<String>) -> List<String>,
        progressMessage: (done: Int, total: Int) -> String,
        concurrency: Int = 1,
        onProgressUpdate: (progress: Float, message: String) -> Unit
    ): List<String> {
        val chunks = segments.chunked(chunkSize)
        val totalSegments = segments.size
        val translatedCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        val doneCount = AtomicInteger(0)

        fun buildPart(chunk: List<TranscriptSegment>): Triple<List<String>, List<Int>, List<String>> {
            val sourceTexts = chunk.map { it.text }
            val missingTexts = mutableListOf<String>()
            val missingIndexes = mutableListOf<Int>()
            sourceTexts.forEachIndexed { srcIdx, src ->
                if (!translatedCache.containsKey(src)) {
                    missingTexts.add(src)
                    missingIndexes.add(srcIdx)
                }
            }
            return Triple(sourceTexts, missingIndexes, missingTexts)
        }

        val results = if (concurrency <= 1) {
            chunks.mapIndexed { idx, chunk ->
                onProgressUpdate(
                    (0.65f + 0.3f * idx / chunks.size.coerceAtLeast(1)).coerceIn(0f, 0.98f),
                    progressMessage(doneCount.get(), totalSegments)
                )
                val (sourceTexts, missingIndexes, missingTexts) = buildPart(chunk)
                val part = MutableList(sourceTexts.size) { i -> translatedCache[sourceTexts[i]] ?: sourceTexts[i] }
                if (missingTexts.isNotEmpty()) {
                    val missingTranslated = translateWithRetry(missingTexts, translateChunk, metrics = metrics)
                    missingIndexes.forEachIndexed { missIdx, srcIdx ->
                        val translatedText = missingTranslated.getOrNull(missIdx) ?: return@forEachIndexed
                        translatedCache[sourceTexts[srcIdx]] = translatedText
                        part[srcIdx] = translatedText
                    }
                }
                doneCount.addAndGet(chunk.size)
                part
            }
        } else {
            val semaphore = Semaphore(concurrency)
            coroutineScope {
                chunks.mapIndexed { idx, chunk ->
                    async {
                        semaphore.withPermit {
                            val (sourceTexts, missingIndexes, missingTexts) = buildPart(chunk)
                            val part = MutableList(sourceTexts.size) { i -> translatedCache[sourceTexts[i]] ?: sourceTexts[i] }
                            if (missingTexts.isNotEmpty()) {
                                val missingTranslated = translateWithRetry(missingTexts, translateChunk, metrics = metrics)
                                missingIndexes.forEachIndexed { missIdx, srcIdx ->
                                    val translatedText = missingTranslated.getOrNull(missIdx) ?: return@forEachIndexed
                                    translatedCache[sourceTexts[srcIdx]] = translatedText
                                    part[srcIdx] = translatedText
                                }
                            }
                            val done = doneCount.addAndGet(chunk.size)
                            onProgressUpdate(
                                (0.65f + 0.3f * done / totalSegments).coerceIn(0f, 0.98f),
                                progressMessage(done, totalSegments)
                            )
                            part
                        }
                    }
                }.awaitAll()
            }
        }

        onProgressUpdate(0.95f, progressMessage(totalSegments, totalSegments))
        return results.flatten()
    }

    private suspend fun translateWithRetry(
        sourceTexts: List<String>,
        translateChunk: suspend (texts: List<String>) -> List<String>,
        maxRetries: Int = 1,
        splitDepth: Int = 0,
        metrics: TranslationMetrics,
    ): List<String> {
        val uniqueTexts = LinkedHashMap<String, MutableList<Int>>()
        sourceTexts.forEachIndexed { i, text ->
            uniqueTexts.getOrPut(text) { mutableListOf() }.add(i)
        }
        val uniqueKeys = uniqueTexts.keys.toList()

        var lastError: Throwable? = null
        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    delay(1500L)
                    metrics.retryCount.incrementAndGet()
                }
                metrics.requestCount.incrementAndGet()

                val timeoutMs = if (uniqueKeys.size > 8) 35_000L else 20_000L
                val uniqueTranslated = withTimeout(timeoutMs) {
                    translateChunk(uniqueKeys)
                }

                if (uniqueTranslated.size != uniqueKeys.size) {
                    throw IllegalStateException("模型返回条数不一致 (请求 ${uniqueKeys.size} 条，返回 ${uniqueTranslated.size} 条)")
                }

                val part = MutableList(sourceTexts.size) { sourceTexts[it] }
                uniqueTexts.entries.forEachIndexed { uIdx, (_, indexes) ->
                    val translatedText = uniqueTranslated[uIdx]
                    indexes.forEach { srcIdx -> part[srcIdx] = translatedText }
                }
                return part
            } catch (e: TimeoutCancellationException) {
                lastError = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (sourceTexts.size > 1 && splitDepth < 1) {
            val mid = sourceTexts.size / 2
            val leftPart = sourceTexts.subList(0, mid)
            val rightPart = sourceTexts.subList(mid, sourceTexts.size)

            val left = translateWithRetry(leftPart, translateChunk, maxRetries = 0, splitDepth = splitDepth + 1, metrics = metrics)
            val right = translateWithRetry(rightPart, translateChunk, maxRetries = 0, splitDepth = splitDepth + 1, metrics = metrics)

            return left + right
        }

        System.err.println(
            "[kaptionit] 翻译直接放弃降级为原文（深度 ${splitDepth}）：${lastError?.message ?: "unknown"}",
        )
        return sourceTexts
    }
}
