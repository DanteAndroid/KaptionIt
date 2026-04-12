package com.danteandroid.transbee.process

import com.danteandroid.transbee.process.MdTranslator.translateParagraphs
import com.danteandroid.transbee.settings.PdfTranslateFormat
import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.translate.AppleTranslateBinary
import com.danteandroid.transbee.translate.AppleTranslator
import com.danteandroid.transbee.translate.DeepLTranslator
import com.danteandroid.transbee.translate.GeminiTranslator
import com.danteandroid.transbee.translate.GoogleTranslator
import com.danteandroid.transbee.translate.GlossaryLoader
import com.danteandroid.transbee.translate.OpenAiTranslator
import com.danteandroid.transbee.translate.TargetLanguageMapper
import com.danteandroid.transbee.translate.TranslationEngine
import com.danteandroid.transbee.utils.JvmResourceStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.err_apple_translate_missing
import java.util.concurrent.atomic.AtomicInteger

/**
 * Markdown 段落翻译工具：分段 → 并行翻译 → 组装双语输出。
 */
object MdTranslator {

    /** 按空行拆分段落，每个段落保留原始文本（含换行） */
    fun splitParagraphs(md: String): List<String> {
        val paragraphs = mutableListOf<String>()
        val buf = StringBuilder()
        for (line in md.lines()) {
            if (line.isBlank()) {
                if (buf.isNotEmpty()) {
                    paragraphs.add(buf.toString().trimEnd())
                    buf.clear()
                }
            } else {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(line)
            }
        }
        if (buf.isNotEmpty()) paragraphs.add(buf.toString().trimEnd())
        return paragraphs
    }

    /** 判断段落是否应跳过翻译（代码块、图片、纯符号等） */
    private fun shouldSkip(paragraph: String): Boolean {
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) return true
        // 代码块
        if (trimmed.startsWith("```")) return true
        // 纯图片链接
        if (trimmed.startsWith("![") && trimmed.contains("](")) return true
        // 无实际文字内容（纯符号/数字/空白）
        if (!trimmed.any { Character.isLetter(it) }) return true
        return false
    }

    /** 需翻译的段落数（与 [translateParagraphs] 内统计一致） */
    fun countTranslatableParagraphs(paragraphs: List<String>): Int =
        paragraphs.count { !shouldSkip(it) }

    /**
     * 翻译段落列表。并发批量提交，在不超时的前提下最大化性能。
     * @return 与 [paragraphs] 等长的翻译结果列表
     */
    suspend fun translateParagraphs(
        paragraphs: List<String>,
        cfg: ToolingSettings,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<String> {
        val forcedTerms = GlossaryLoader.normalizeMappings(
            cfg.forcedTranslationTerms.map { GlossaryLoader.TermMapping(source = it.source, target = it.target) }
        )
        val translatable = mutableListOf<IndexedValue<String>>()
        val result = MutableList(paragraphs.size) { paragraphs[it] }

        paragraphs.forEachIndexed { i, p ->
            if (!shouldSkip(p)) translatable.add(IndexedValue(i, p))
        }

        if (translatable.isEmpty()) return result

        val total = translatable.size
        val doneCount = AtomicInteger(0)
        val chunkSize: Int
        val concurrency: Int

        val translateChunk: suspend (List<String>) -> List<String>

        when (cfg.translationEngine) {
            TranslationEngine.APPLE -> {
                chunkSize = 6
                concurrency = 1
                val bin = AppleTranslateBinary.resolvePath(cfg.appleTranslateBinary)
                    ?: error(JvmResourceStrings.text(Res.string.err_apple_translate_missing))
                val appleSource =
                    TargetLanguageMapper.toAppleLocale(cfg.targetLanguage, forTarget = false)
                val appleTarget =
                    TargetLanguageMapper.toAppleLocale(cfg.targetLanguage, forTarget = true)
                val translator = AppleTranslator(binaryPath = bin)
                translateChunk =
                    { texts ->
                        translator.translateBatch(texts, appleSource, appleTarget)
                    }
            }

            TranslationEngine.GOOGLE -> {
                chunkSize = 50
                concurrency = 8
                val target = TargetLanguageMapper.toGoogleTargetCode(cfg.targetLanguage)
                val translator = GoogleTranslator(apiKey = cfg.googleApiKey)
                translateChunk = { texts ->
                    translator.translateBatch(texts, target)
                }
            }

            TranslationEngine.DEEPL -> {
                chunkSize = 20
                concurrency = 6
                val target = TargetLanguageMapper.toDeepLTargetCode(cfg.targetLanguage)
                val translator =
                    DeepLTranslator(authKey = cfg.deeplApiKey, useFreeApiHost = cfg.deeplUseFreeApi)
                translateChunk = { texts ->
                    translator.translateBatch(texts, target)
                }
            }

            TranslationEngine.GEMINI -> {
                chunkSize = 10
                concurrency = 2
                val translator = GeminiTranslator(
                    apiKey = cfg.geminiApiKey,
                    model = cfg.geminiModel,
                    glossaryMappings = forcedTerms,
                    userPrompt = cfg.translationPrompt,
                    enforceSubtitleBatchRules = false,
                )
                translateChunk = { texts ->
                    translator.translateBatch(texts, cfg.targetLanguage).texts
                }
            }

            TranslationEngine.OPENAI -> {
                chunkSize = 10
                concurrency = 2
                val translator = OpenAiTranslator(
                    apiKey = cfg.openAiKey,
                    model = cfg.openAiModel,
                    baseUrl = cfg.openAiBaseUrl,
                    enforceSubtitleBatchRules = false,
                    glossaryMappings = forcedTerms,
                    userPrompt = cfg.translationPrompt,
                )
                translateChunk = { texts ->
                    translator.translateBatch(texts, cfg.targetLanguage).texts
                }
            }
        }

        val chunks = translatable.chunked(chunkSize)
        val semaphore = Semaphore(concurrency)

        coroutineScope {
            chunks.mapIndexed { chunkIdx, chunk ->
                async {
                    semaphore.withPermit {
                        // 附加前后各 2 个段落作为上下文（不翻译），帮助 LLM 理解边界段落
                        val contextBefore = buildList {
                            if (chunkIdx > 0) {
                                val prev = chunks[chunkIdx - 1]
                                addAll(prev.takeLast(2).map { it.value })
                            }
                        }
                        val contextAfter = buildList {
                            if (chunkIdx < chunks.size - 1) {
                                val next = chunks[chunkIdx + 1]
                                addAll(next.take(2).map { it.value })
                            }
                        }
                        val texts = contextBefore + chunk.map { it.value } + contextAfter
                        val translated = translateChunk(texts)
                        // 只取中间段的翻译结果（跳过上下文）
                        val offset = contextBefore.size
                        chunk.forEachIndexed { j, iv ->
                            result[iv.index] = translated.getOrElse(j + offset) { iv.value }
                        }
                        val done = doneCount.addAndGet(chunk.size)
                        onProgress(done, total)
                    }
                }
            }.awaitAll()
        }

        return result
    }

    /** 按指定格式组装双语 Markdown */
    fun assemble(
        originals: List<String>,
        translations: List<String>,
        format: PdfTranslateFormat,
    ): String = buildString {
        when (format) {
            PdfTranslateFormat.BILINGUAL -> {
                for (i in originals.indices) {
                    if (i > 0) append("\n\n")
                    append(originals[i])
                    if (originals[i] != translations[i]) {
                        append("\n\n")
                        append(translations[i])
                    }
                }
            }

            PdfTranslateFormat.ORIGINAL_FIRST -> {
                append(originals.joinToString("\n\n"))
                append("\n\n---\n\n")
                append(translations.joinToString("\n\n"))
            }

            PdfTranslateFormat.TRANSLATION_FIRST -> {
                append(translations.joinToString("\n\n"))
                append("\n\n---\n\n")
                append(originals.joinToString("\n\n"))
            }

            PdfTranslateFormat.SOURCE_ONLY -> {
                append(originals.joinToString("\n\n"))
            }
        }
        append('\n')
    }
}
