package com.danteandroid.transbee.translate

import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.utils.JvmResourceStrings
import com.danteandroid.transbee.utils.OsUtils
import com.danteandroid.transbee.whisper.TranscriptSegment
import com.danteandroid.transbee.whisper.WhisperParseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_apple_binary
import transbee.composeapp.generated.resources.err_apple_translate_macos_only
import transbee.composeapp.generated.resources.err_llm_count_mismatch
import transbee.composeapp.generated.resources.err_translation_no_response
import transbee.composeapp.generated.resources.hint_apple_translate_pair
import transbee.composeapp.generated.resources.msg_translate_apple_progress
import transbee.composeapp.generated.resources.msg_translate_apple_running
import transbee.composeapp.generated.resources.msg_translate_deepl_progress
import transbee.composeapp.generated.resources.msg_translate_deepl_running
import transbee.composeapp.generated.resources.msg_translate_google_progress
import transbee.composeapp.generated.resources.msg_translate_gemini_progress
import transbee.composeapp.generated.resources.msg_translate_gemini_running
import transbee.composeapp.generated.resources.msg_translate_google_running
import transbee.composeapp.generated.resources.msg_translate_openai_progress
import transbee.composeapp.generated.resources.msg_translate_openai_running
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

data class SegmentTranslationOutcome(
    val translations: List<String>,
    /** 与 segments 一一对应；仅 OpenAI 字幕翻译且配置了专业词库时非 null */
    val sourceNeedsRelaxedTermCorrection: List<Boolean>?,
)

private data class TranslationChunkOutput(
    val texts: List<String>,
    val nc: List<Boolean>?,
)

object TranslationService {

    suspend fun translateSegments(
        cfg: ToolingSettings,
        whisperDoc: WhisperParseResult,
        segments: List<TranscriptSegment>,
        metrics: TranslationMetrics,
        onProgressUpdate: (progress: Float, message: String) -> Unit
    ): SegmentTranslationOutcome {
        val engine = cfg.translationEngine
        val forcedTerms = GlossaryLoader.normalizeMappings(
            cfg.forcedTranslationTerms.map { GlossaryLoader.TermMapping(source = it.source, target = it.target) }
        )
        val appleSource = TargetLanguageMapper.whisperLanguageToAppleSource(whisperDoc.whisperLanguage)
        val appleTarget = TargetLanguageMapper.toAppleLocale(cfg.targetLanguage, forTarget = true)
        val googleTarget = TargetLanguageMapper.toGoogleTargetCode(cfg.targetLanguage)
        val deeplTarget = TargetLanguageMapper.toDeepLTargetCode(cfg.targetLanguage)
        
        onProgressUpdate(
            0f,
            when (engine) {
                TranslationEngine.APPLE -> JvmResourceStrings.text(Res.string.msg_translate_apple_running)
                TranslationEngine.GOOGLE -> JvmResourceStrings.text(Res.string.msg_translate_google_running)
                TranslationEngine.DEEPL -> JvmResourceStrings.text(Res.string.msg_translate_deepl_running)
                TranslationEngine.GEMINI -> JvmResourceStrings.text(Res.string.msg_translate_gemini_running)
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
                            TranslationChunkOutput(
                                texts = translator.translateBatch(
                                    texts = texts,
                                    sourceAppleLocale = appleSource,
                                    targetAppleLocale = appleTarget,
                                ),
                                nc = null,
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
                    onProgressUpdate = onProgressUpdate,
                    translationEngine = TranslationEngine.APPLE,
                    collectNeedCorrectFlags = false,
                )
            }

            TranslationEngine.GOOGLE -> {
                val translator = GoogleTranslator(apiKey = cfg.googleApiKey)
                translateByChunk(
                    segments = segments,
                    chunkSize = 100,
                    metrics = metrics,
                    translateChunk = { texts ->
                        TranslationChunkOutput(
                            texts = translator.translateBatch(
                                texts = texts,
                                targetGoogleCode = googleTarget,
                            ),
                            nc = null,
                        )
                    },
                    progressMessage = { done, total ->
                        JvmResourceStrings.text(Res.string.msg_translate_google_progress, done, total)
                    },
                    concurrency = TranslationConcurrency,
                    onProgressUpdate = onProgressUpdate,
                    translationEngine = TranslationEngine.GOOGLE,
                    collectNeedCorrectFlags = false,
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
                        TranslationChunkOutput(
                            texts = translator.translateBatch(
                                texts = texts,
                                targetDeepL = deeplTarget,
                            ),
                            nc = null,
                        )
                    },
                    progressMessage = { done, total ->
                        JvmResourceStrings.text(Res.string.msg_translate_deepl_progress, done, total)
                    },
                    concurrency = TranslationConcurrency,
                    onProgressUpdate = onProgressUpdate,
                    translationEngine = TranslationEngine.DEEPL,
                    collectNeedCorrectFlags = false,
                )
            }

            TranslationEngine.GEMINI -> {
                val translator = GeminiTranslator(
                    apiKey = cfg.geminiApiKey,
                    model = cfg.geminiModel,
                    glossaryMappings = forcedTerms,
                    userPrompt = cfg.translationPrompt,
                )
                translateByChunk(
                    segments = segments,
                    chunkSize = TranslationChunkSizeOpenAiStyle,
                    metrics = metrics,
                    translateChunk = { texts ->
                        val batch = translator.translateBatch(
                            texts = texts,
                            targetLanguage = cfg.targetLanguage,
                        )
                        TranslationChunkOutput(
                            texts = batch.texts,
                            nc = if (forcedTerms.isNotEmpty()) batch.nc else null,
                        )
                    },
                    progressMessage = { done, total ->
                        JvmResourceStrings.text(Res.string.msg_translate_gemini_progress, done, total)
                    },
                    concurrency = TranslationConcurrencyOpenAi,
                    onProgressUpdate = onProgressUpdate,
                    translationEngine = TranslationEngine.GEMINI,
                    collectNeedCorrectFlags = forcedTerms.isNotEmpty(),
                )
            }

            TranslationEngine.OPENAI -> {
                val translator = OpenAiTranslator(
                    apiKey = cfg.openAiKey,
                    model = cfg.openAiModel,
                    baseUrl = cfg.openAiBaseUrl,
                    glossaryMappings = forcedTerms,
                    userPrompt = cfg.translationPrompt,
                )
                translateByChunk(
                    segments = segments,
                    chunkSize = TranslationChunkSizeOpenAiStyle,
                    metrics = metrics,
                    translateChunk = { texts ->
                        val batch = translator.translateBatch(
                            texts = texts,
                            targetLanguage = cfg.targetLanguage,
                        )
                        TranslationChunkOutput(
                            texts = batch.texts,
                            nc = if (forcedTerms.isNotEmpty()) batch.nc else null,
                        )
                    },
                    progressMessage = { done, total ->
                        JvmResourceStrings.text(Res.string.msg_translate_openai_progress, done, total)
                    },
                    concurrency = TranslationConcurrencyOpenAi,
                    onProgressUpdate = onProgressUpdate,
                    translationEngine = TranslationEngine.OPENAI,
                    collectNeedCorrectFlags = forcedTerms.isNotEmpty(),
                )
            }
        }
    }

    private suspend fun translateByChunk(
        segments: List<TranscriptSegment>,
        chunkSize: Int,
        metrics: TranslationMetrics,
        translateChunk: suspend (texts: List<String>) -> TranslationChunkOutput,
        progressMessage: (done: Int, total: Int) -> String,
        concurrency: Int = 1,
        onProgressUpdate: (progress: Float, message: String) -> Unit,
        translationEngine: TranslationEngine,
        collectNeedCorrectFlags: Boolean = false,
    ): SegmentTranslationOutcome {
        val chunks = segments.chunked(chunkSize)
        val totalSegments = segments.size
        val translatedCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        val needCorrectCache = if (
            (translationEngine == TranslationEngine.OPENAI || translationEngine == TranslationEngine.GEMINI) &&
            collectNeedCorrectFlags
        ) {
            java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        } else {
            null
        }
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
            chunks.map { chunk ->
                val (sourceTexts, missingIndexes, missingTexts) = buildPart(chunk)
                val part = MutableList(sourceTexts.size) { i -> translatedCache[sourceTexts[i]] ?: sourceTexts[i] }
                if (missingTexts.isNotEmpty()) {
                    val missingOut = translateWithRetry(missingTexts, translateChunk, metrics = metrics)
                    missingIndexes.forEachIndexed { missIdx, srcIdx ->
                        val translatedText = missingOut.texts.getOrNull(missIdx) ?: return@forEachIndexed
                        translatedCache[sourceTexts[srcIdx]] = translatedText
                        part[srcIdx] = translatedText
                        missingOut.nc?.getOrNull(missIdx)?.let { nc ->
                            needCorrectCache?.set(sourceTexts[srcIdx], nc)
                        }
                    }
                }
                doneCount.addAndGet(chunk.size)
                onProgressUpdate(
                    (doneCount.get().toFloat() / totalSegments).coerceIn(0f, 0.99f),
                    progressMessage(doneCount.get(), totalSegments),
                )
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
                                val missingOut = translateWithRetry(missingTexts, translateChunk, metrics = metrics)
                                missingIndexes.forEachIndexed { missIdx, srcIdx ->
                                    val translatedText = missingOut.texts.getOrNull(missIdx) ?: return@forEachIndexed
                                    translatedCache[sourceTexts[srcIdx]] = translatedText
                                    part[srcIdx] = translatedText
                                    missingOut.nc?.getOrNull(missIdx)?.let { nc ->
                                        needCorrectCache?.set(sourceTexts[srcIdx], nc)
                                    }
                                }
                            }
                            val done = doneCount.addAndGet(chunk.size)
                            onProgressUpdate(
                                (done.toFloat() / totalSegments).coerceIn(0f, 0.99f),
                                progressMessage(done, totalSegments),
                            )
                            part
                        }
                    }
                }.awaitAll()
            }
        }

        onProgressUpdate(1f, progressMessage(totalSegments, totalSegments))
        val translations = results.flatten()
        val flags = if (
            collectNeedCorrectFlags &&
            (translationEngine == TranslationEngine.OPENAI || translationEngine == TranslationEngine.GEMINI) &&
            needCorrectCache != null
        ) {
            segments.map { seg -> needCorrectCache[seg.text] == true }
        } else {
            null
        }
        return SegmentTranslationOutcome(translations = translations, sourceNeedsRelaxedTermCorrection = flags)
    }

    private suspend fun translateWithRetry(
        sourceTexts: List<String>,
        translateChunk: suspend (texts: List<String>) -> TranslationChunkOutput,
        maxRetries: Int = 1,
        splitDepth: Int = 0,
        metrics: TranslationMetrics,
    ): TranslationChunkOutput {
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

                val uniqueTranslated = translateChunk(uniqueKeys)

                if (uniqueTranslated.texts.size != uniqueKeys.size) {
                    throw IllegalStateException(
                        JvmResourceStrings.text(
                            Res.string.err_llm_count_mismatch,
                            uniqueKeys.size,
                            uniqueTranslated.texts.size,
                        ),
                    )
                }

                val part = MutableList(sourceTexts.size) { sourceTexts[it] }
                val flagsPart = MutableList(sourceTexts.size) { false }
                uniqueTexts.entries.forEachIndexed { uIdx, (_, indexes) ->
                    val translatedText = uniqueTranslated.texts[uIdx]
                    val flag = uniqueTranslated.nc?.getOrNull(uIdx) ?: false
                    indexes.forEach { srcIdx ->
                        part[srcIdx] = translatedText
                        flagsPart[srcIdx] = flag
                    }
                }
                return TranslationChunkOutput(
                    texts = part,
                    nc = uniqueTranslated.nc?.let { flagsPart },
                )
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

            return TranslationChunkOutput(
                texts = left.texts + right.texts,
                nc = when {
                    left.nc != null && right.nc != null -> left.nc + right.nc
                    else -> null
                },
            )
        }

        if (lastError != null) throw lastError
        error(JvmResourceStrings.text(Res.string.err_translation_no_response))
    }
}
