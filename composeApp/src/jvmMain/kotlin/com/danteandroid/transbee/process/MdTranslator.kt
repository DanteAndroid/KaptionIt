package com.danteandroid.transbee.process

import com.danteandroid.transbee.process.MdTranslator.translateParagraphs
import com.danteandroid.transbee.settings.PdfTranslateFormat
import com.danteandroid.transbee.settings.ToolingSettings
import com.danteandroid.transbee.translate.AppleTranslateBinary
import com.danteandroid.transbee.translate.AppleTranslator
import com.danteandroid.transbee.translate.DeepLTranslator
import com.danteandroid.transbee.translate.GoogleTranslator
import com.danteandroid.transbee.translate.OpenAiTranslator
import com.danteandroid.transbee.translate.TargetLanguageMapper
import com.danteandroid.transbee.translate.TranslationEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
                    ?: error("找不到本机翻译组件")
                val appleSource =
                    TargetLanguageMapper.toAppleLocale(cfg.targetLanguage, forTarget = false)
                val appleTarget =
                    TargetLanguageMapper.toAppleLocale(cfg.targetLanguage, forTarget = true)
                val translator = AppleTranslator(binaryPath = bin)
                translateChunk =
                    { texts -> translator.translateBatch(texts, appleSource, appleTarget) }
            }

            TranslationEngine.GOOGLE -> {
                chunkSize = 50
                concurrency = 8
                val target = TargetLanguageMapper.toGoogleTargetCode(cfg.targetLanguage)
                val translator = GoogleTranslator(apiKey = cfg.googleApiKey)
                translateChunk = { texts -> translator.translateBatch(texts, target) }
            }

            TranslationEngine.DEEPL -> {
                chunkSize = 20
                concurrency = 6
                val target = TargetLanguageMapper.toDeepLTargetCode(cfg.targetLanguage)
                val translator =
                    DeepLTranslator(authKey = cfg.deeplApiKey, useFreeApiHost = cfg.deeplUseFreeApi)
                translateChunk = { texts -> translator.translateBatch(texts, target) }
            }

            TranslationEngine.OPENAI -> {
                chunkSize = 10
                concurrency = 2
                val translator = OpenAiTranslator(
                    apiKey = cfg.openAiKey,
                    model = cfg.openAiModel,
                    baseUrl = cfg.openAiBaseUrl,
                    enforceSubtitleBatchRules = false,
                )
                translateChunk = { texts -> translator.translateBatch(texts, cfg.targetLanguage) }
            }
        }

        val chunks = translatable.chunked(chunkSize)
        val semaphore = Semaphore(concurrency)

        coroutineScope {
            chunks.map { chunk ->
                async {
                    semaphore.withPermit {
                        val texts = chunk.map { it.value }
                        val translated = translateChunk(texts)
                        chunk.forEachIndexed { j, iv ->
                            result[iv.index] = translated.getOrElse(j) { iv.value }
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
        }
        append('\n')
    }
}
