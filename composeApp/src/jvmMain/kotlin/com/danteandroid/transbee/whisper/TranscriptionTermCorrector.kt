package com.danteandroid.transbee.whisper

import com.danteandroid.transbee.utils.TransbeeLog
import kotlin.math.abs
import org.apache.commons.codec.language.DoubleMetaphone
import org.apache.commons.codec.language.MatchRatingApproachEncoder

/**
 * 转录术语：字面匹配；模型返回 nc=true 的宽松路径在扩窗内用 Double Metaphone（及扩展下的码模糊 / MatchRating）判定音似。
 */
object TranscriptionTermCorrector {

    private val glossaryWhitespaceSplit = Regex("\\s+")

    private const val LOW_PROB_THRESHOLD = 0.6

    /** 低 p 合并后的纠错窗口最多包含几个 Whisper token，避免整句误匹配术语 */
    private const val MAX_PIECE_SPAN = 5

    /** 短串不做 Metaphone 音似判断，避免 the/a 等误命中 */
    private const val MIN_PHONETIC_COMPARE_LEN = 4

    private val metaphone = DoubleMetaphone().apply { maxCodeLen = 16 }
    private val matchRating = MatchRatingApproachEncoder()
    private val wordPattern = Regex("""[\w']+""")

    /** 识别里紧跟在术语后的单字母噪声（如 “Quiver” → “quiver K”），不吞掉 a/A/i/I 以免误伤正常英语 */
    private val singleLetterNoiseBlacklist = setOf("a", "A", "i", "I")

    /** 单字母 K 常被写成 “Kay” 等音节 */
    private val straySyllableTails = setOf("kay", "key")

    private data class WordSpan(val text: String, val start: Int, val end: Int)

    fun polish(result: WhisperParseResult, glossarySources: List<String>): WhisperParseResult {
        val terms = normalizeGlossarySources(glossarySources)
        if (terms.isEmpty()) return result

        val sortedTerms = terms.sortedWith(
            compareByDescending<String> { it.split(glossaryWhitespaceSplit).size }
                .thenByDescending { it.length },
        )

        return result.copy(
            segments = result.segments.map { segment ->
                val newText = when {
                    !segment.wordPieces.isNullOrEmpty() ->
                        replaceGlossarySpansLowProbGated(segment, sortedTerms)
                    else -> replaceGlossarySpans(segment.text, sortedTerms, relaxedPhonetics = false)
                }
                val keepPieces = if (newText == segment.text) segment.wordPieces else null
                segment.copy(text = newText, wordPieces = keepPieces)
            },
        )
    }

    fun polishRelaxedByNeedCorrect(
        result: WhisperParseResult,
        glossarySources: List<String>,
        nc: List<Boolean>,
    ): WhisperParseResult {
        require(result.segments.size == nc.size) {
            "nc.size (${nc.size}) != segments.size (${result.segments.size})"
        }
        val terms = normalizeGlossarySources(glossarySources)
        if (terms.isEmpty()) return result
        val sortedTerms = terms.sortedWith(
            compareByDescending<String> { it.split(glossaryWhitespaceSplit).size }
                .thenByDescending { it.length },
        )
        return result.copy(
            segments = result.segments.mapIndexed { idx, segment ->
                if (!nc.getOrElse(idx) { false }) return@mapIndexed segment
                val newText = replaceGlossarySpans(
                    segment.text,
                    sortedTerms,
                    relaxedPhonetics = true,
                    extendedAsrPhonetics = true,
                )
                if (newText != segment.text) {
                    logPostTranscriptionEdit(
                        phase = "relaxed",
                        index = idx,
                        startSec = segment.startSec,
                        endSec = segment.endSec,
                        before = segment.text,
                        after = newText,
                    )
                }
                val keepPieces = if (newText == segment.text) segment.wordPieces else null
                segment.copy(text = newText, wordPieces = keepPieces)
            },
        )
    }

    private fun logPostTranscriptionEdit(
        phase: String,
        index: Int,
        startSec: Double,
        endSec: Double,
        before: String,
        after: String,
    ) {
        TransbeeLog.verbose {
            buildString {
                appendLine("[TermCorr/$phase] #$index [${formatSec(startSec)}–${formatSec(endSec)}]")
                appendLine("  - $before")
                appendLine("  + $after")
            }
        }
    }

    private fun formatSec(s: Double): String = "%.2f".format(s)

    private fun replaceGlossarySpansLowProbGated(
        segment: TranscriptSegment,
        termsByOrder: List<String>,
    ): String {
        val pieces = segment.wordPieces ?: return segment.text
        if (pieces.isEmpty()) return segment.text
        val text = segment.text
        val lowPieceIdx = pieces.indices.filter { i ->
            pieces[i].probability?.let { it < LOW_PROB_THRESHOLD } == true
        }
        if (lowPieceIdx.isEmpty()) return text

        val charRanges = mapPiecesToCharRangesInText(text, pieces) ?: run {
            return replaceGlossarySpans(text, termsByOrder, relaxedPhonetics = false)
        }

        val last = pieces.lastIndex
        val rawIntervals = lowPieceIdx.map { i ->
            (i - 1).coerceAtLeast(0)..(i + 1).coerceAtMost(last)
        }
        val merged = mergeWordIntervals(rawIntervals)
        val mergedExtended = extendMergedWithTrailingStrayPieces(merged, pieces, last)
            .map { capPieceIndexRange(it, last) }

        var result = text
        val spans = mergedExtended.map { pr ->
            val start = charRanges[pr.first].first
            val endExclusive = charRanges[pr.last].last + 1
            start until endExclusive
        }.sortedByDescending { it.first }

        for (range in spans) {
            val endExcl = range.last + 1
            val sub = result.substring(range.first, endExcl)
            val fixed = replaceGlossarySpans(sub, termsByOrder, relaxedPhonetics = false)
            if (fixed != sub) {
                result = result.replaceRange(range.first, endExcl, fixed)
            }
        }
        return result
    }

    private fun mapPiecesToCharRangesInText(
        text: String,
        pieces: List<TranscriptWordPiece>,
    ): List<IntRange>? {
        val out = ArrayList<IntRange>(pieces.size)
        var from = 0
        for (i in pieces.indices) {
            val p = pieces[i]
            val t = p.text.trim()
            if (t.isEmpty()) return null
            val idx = text.indexOf(t, from, ignoreCase = true)
            if (idx < 0) return null
            out.add(idx until idx + t.length)
            from = idx + t.length
        }
        return out
    }

    private fun extendMergedWithTrailingStrayPieces(
        merged: List<IntRange>,
        pieces: List<TranscriptWordPiece>,
        lastIndex: Int,
    ): List<IntRange> {
        val expanded = merged.map { r ->
            var hi = r.last
            while (hi < lastIndex) {
                val next = pieces[hi + 1].text.trim()
                val isStrayTail =
                    (next.length == 1 && next[0].isLetter() && next !in singleLetterNoiseBlacklist) ||
                        next.lowercase() in straySyllableTails
                if (!isStrayTail) break
                hi++
            }
            r.first..hi
        }
        return mergeWordIntervals(expanded)
    }

    private fun capPieceIndexRange(r: IntRange, lastIndex: Int): IntRange {
        val len = r.last - r.first + 1
        if (len <= MAX_PIECE_SPAN) return r
        return r.first..(r.first + MAX_PIECE_SPAN - 1).coerceAtMost(lastIndex)
    }

    private fun mergeWordIntervals(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val out = mutableListOf<IntRange>()
        var cur = sorted[0]
        for (i in 1 until sorted.size) {
            val n = sorted[i]
            cur = if (n.first <= cur.last + 1) {
                cur.first..maxOf(cur.last, n.last)
            } else {
                out.add(cur)
                n
            }
        }
        out.add(cur)
        return out
    }

    private fun normalizeGlossarySources(raw: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        for (s in raw) {
            val trimmed = s.trim()
            if (trimmed.isEmpty()) continue
            if (seen.add(trimmed.lowercase())) out.add(trimmed)
        }
        return out
    }

    /** 标准：Double Metaphone 主/次码四路相等 + 音节差≤1；短串不比音。 */
    private fun soundsLikeStrict(a: String, b: String): Boolean {
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        if (x == y || x.replace(" ", "") == y.replace(" ", "")) return true
        if (x.length < MIN_PHONETIC_COMPARE_LEN || y.length < MIN_PHONETIC_COMPARE_LEN) return false

        val p1 = metaphone.doubleMetaphone(x) ?: ""
        val s1 = metaphone.doubleMetaphone(x, true) ?: ""
        val p2 = metaphone.doubleMetaphone(y) ?: ""
        val s2 = metaphone.doubleMetaphone(y, true) ?: ""
        if (p1.isEmpty() || p2.isEmpty()) return false

        val codesMatch = p1 == p2 || p1 == s2 || s1 == p2 || s1 == s2
        if (!codesMatch) return false
        return abs(getSyllableCount(x) - getSyllableCount(y)) <= 1
    }

    /**
     * 两词窗且第二词为 ASR 常见尾噪（单字母辅音或 kay/key）时，才允许用 [MatchRatingApproachEncoder]，
     * 否则 MRA 会把「I could / that」等与无关术语判成同码，造成整句乱替。
     */
    private fun isExtendedMraAsrTailTwoWordSpan(span: String): Boolean {
        val parts = span.trim().split(Regex("\\s+"))
        if (parts.size != 2) return false
        val tail = parts[1].trim()
        if (tail.length == 1 && tail[0].isLetter() && tail !in singleLetterNoiseBlacklist) return true
        if (tail.lowercase() in straySyllableTails) return true
        return false
    }

    /** 两词窗对单字术语时，码模糊也易误判（如 K at / billet）；与 MRA 共用尾噪条件。 */
    private fun soundsLikeFuzzyForExtendedAsr(span: String, term: String): Boolean {
        if (!soundsLikeFuzzyMetaphoneCodes(span, term)) return false
        if (wordCount(span) == 2 && wordCount(term) == 1 && !isExtendedMraAsrTailTwoWordSpan(span)) return false
        return true
    }

    /**
     * 仅在模型返回 nc=true 的宽松路径（[extendedAsrPhonetics]=true）追加 Metaphone 码 Levenshtein≤1 与
     * 受控的 [MatchRatingApproachEncoder]（仅 [isExtendedMraAsrTailTwoWordSpan]）；
     * 全段严格纠错仍只用 [soundsLikeStrict]。
     */
    private fun phoneticMatch(span: String, term: String, extendedAsr: Boolean): Boolean {
        if (soundsLikeStrict(span, term)) return true
        if (!extendedAsr) return false
        if (soundsLikeFuzzyForExtendedAsr(span, term)) return true
        if (!isExtendedMraAsrTailTwoWordSpan(span)) return false
        return matchRating.isEncodeEquals(span, term)
    }

    private fun isFuzzyMetaphoneMatch(code1: String, code2: String, str1: String, str2: String): Boolean {
        if (code1.isEmpty() || code2.isEmpty()) return false
        val dist = levenshtein(code1, code2)
        val maxLen = maxOf(code1.length, code2.length)
        val syl1 = getSyllableCount(str1)
        val syl2 = getSyllableCount(str2)
        if (dist == 0) return syl1 == syl2
        if (wordCount(str1) == 1 && wordCount(str2) == 1) return false
        if (maxLen < 5 || dist > 1) return false
        if (code1.take(maxLen - dist) != code2.take(maxLen - dist)) return false
        if (levenshtein(getConsonants(str1), getConsonants(str2)) > 1) return false
        return abs(syl1 - syl2) <= 1
    }

    private fun soundsLikeFuzzyMetaphoneCodes(a: String, b: String): Boolean {
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        if (x.length < MIN_PHONETIC_COMPARE_LEN || y.length < MIN_PHONETIC_COMPARE_LEN) return false
        val m1c1 = metaphone.doubleMetaphone(x) ?: ""
        val m1c2 = metaphone.doubleMetaphone(x, true) ?: ""
        val m2c1 = metaphone.doubleMetaphone(y) ?: ""
        val m2c2 = metaphone.doubleMetaphone(y, true) ?: ""
        return isFuzzyMetaphoneMatch(m1c1, m2c1, x, y) ||
            isFuzzyMetaphoneMatch(m1c1, m2c2, x, y) ||
            isFuzzyMetaphoneMatch(m1c2, m2c1, x, y) ||
            isFuzzyMetaphoneMatch(m1c2, m2c2, x, y)
    }

    private fun getConsonants(s: String) =
        s.lowercase().filter { it !in "aeiouy" && it in 'a'..'z' }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev
                else minOf(dp[j - 1], minOf(dp[j], prev)) + 1
                prev = temp
            }
        }
        return dp[b.length]
    }

    /** 供单元测试断言，勿在业务中调用 */
    internal fun soundsLikeStrictForTest(a: String, b: String): Boolean = soundsLikeStrict(a, b)

    internal fun phoneticMatchForTest(span: String, term: String, extended: Boolean): Boolean =
        phoneticMatch(span, term, extended)

    internal fun replaceGlossarySpansForTest(
        text: String,
        terms: List<String>,
        relaxedPhonetics: Boolean,
        extendedAsrPhonetics: Boolean = false,
    ): String = replaceGlossarySpans(text, terms, relaxedPhonetics, extendedAsrPhonetics)

    private fun wordCount(s: String) = s.trim().split(Regex("\\s+")).size

    private fun getSyllableCount(s: String): Int {
        var count = 0
        var prevIsVowel = false
        for (c in s.lowercase()) {
            val isVowel = c in "aeiouy"
            if (isVowel && !prevIsVowel) count++
            prevIsVowel = isVowel
        }
        return count
    }

    private fun tokenizeWords(text: String): List<WordSpan> =
        wordPattern.findAll(text).map { m ->
            WordSpan(m.value, m.range.first, m.range.last + 1)
        }.toList()

    /** 单字术语 + 扩展音似：先试 2 词窗再 1 词；否则仅与词数一致。 */
    private fun relaxedSpanWidths(termWordCount: Int, extendedAsrPhonetics: Boolean): List<Int> =
        when {
            termWordCount > 1 -> listOf(termWordCount)
            extendedAsrPhonetics -> listOf(2, 1)
            else -> listOf(1)
        }

    /** 疑似与词表命中（equals 或音似）的词区间，合并后用于扩窗；逻辑与 Core 内判断一致，避免重复两套规则。 */
    private fun collectRelaxedSuspectWordRanges(
        words: List<WordSpan>,
        termsByOrder: List<String>,
        extendedAsrPhonetics: Boolean,
    ): List<IntRange> {
        val raw = mutableListOf<IntRange>()
        for (term in termsByOrder) {
            val nw = wordCount(term)
            for (w in relaxedSpanWidths(nw, extendedAsrPhonetics)) {
                if (w > words.size) continue
                val lastStart = words.size - w
                for (start in 0..lastStart) {
                    val spanText = words.subList(start, start + w).joinToString(" ") { it.text }
                    if (spanText.equals(term, ignoreCase = true) ||
                        phoneticMatch(spanText, term, extendedAsrPhonetics)
                    ) {
                        raw.add(start..(start + w - 1))
                    }
                }
            }
        }
        if (raw.isEmpty()) return emptyList()
        return mergeWordIntervals(raw)
    }

    private fun expandWordRangesWithNeighbors(ranges: List<IntRange>, lastWordIndex: Int): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val expanded = ranges.map { r ->
            (r.first - 1).coerceAtLeast(0)..(r.last + 1).coerceAtMost(lastWordIndex)
        }
        return mergeWordIntervals(expanded)
    }

    private fun replaceGlossarySpansCore(
        text: String,
        termsByOrder: List<String>,
        relaxedPhonetics: Boolean,
        extendedAsrPhonetics: Boolean,
    ): String {
        val words = tokenizeWords(text)
        if (words.isEmpty()) return text

        fun tryWidthsForTerm(nw: Int): List<Int> =
            when {
                !relaxedPhonetics -> listOf(nw)
                extendedAsrPhonetics && nw == 1 -> listOf(2, 1)
                nw == 1 -> listOf(1)
                else -> listOf(nw)
            }

        val sb = StringBuilder()
        var lastEnd = 0
        var i = 0

        while (i < words.size) {
            var consumed = 0
            var replacement: String? = null

            outer@ for (term in termsByOrder) {
                val nw = wordCount(term)
                for (w in tryWidthsForTerm(nw)) {
                    if (i + w > words.size) continue
                    val spanText = words.subList(i, i + w).joinToString(" ") { it.text }
                    when {
                        spanText.equals(term, ignoreCase = true) -> {
                            replacement = term
                            consumed = w
                            break@outer
                        }
                        relaxedPhonetics && phoneticMatch(spanText, term, extendedAsrPhonetics) -> {
                            replacement = term
                            consumed = w
                            break@outer
                        }
                    }
                }
            }

            if (replacement != null && consumed > 0) {
                sb.append(text.substring(lastEnd, words[i].start))
                sb.append(replacement)
                lastEnd = words[i + consumed - 1].end
                i += consumed
            } else {
                sb.append(text.substring(lastEnd, words[i].end))
                lastEnd = words[i].end
                i++
            }
        }
        sb.append(text.substring(lastEnd))
        return sb.toString()
    }

    private fun replaceGlossarySpans(
        text: String,
        termsByOrder: List<String>,
        relaxedPhonetics: Boolean,
        extendedAsrPhonetics: Boolean = false,
    ): String {
        val words = tokenizeWords(text)
        if (words.isEmpty()) return text

        if (!relaxedPhonetics) {
            return replaceGlossarySpansCore(
                text,
                termsByOrder,
                relaxedPhonetics = false,
                extendedAsrPhonetics = false,
            )
        }

        val suspects = collectRelaxedSuspectWordRanges(words, termsByOrder, extendedAsrPhonetics)
        if (suspects.isEmpty()) {
            return replaceGlossarySpansCore(
                text,
                termsByOrder,
                relaxedPhonetics = true,
                extendedAsrPhonetics,
            )
        }

        val lastIdx = words.lastIndex
        val windows = expandWordRangesWithNeighbors(suspects, lastIdx)
        if (windows.isEmpty()) {
            return replaceGlossarySpansCore(
                text,
                termsByOrder,
                relaxedPhonetics = true,
                extendedAsrPhonetics,
            )
        }

        val charSpans = windows.map { wr ->
            words[wr.first].start until words[wr.last].end
        }.sortedByDescending { it.first }

        var result = text
        for (sp in charSpans) {
            val sub = result.substring(sp.first, sp.last)
            val fixed = replaceGlossarySpansCore(
                sub,
                termsByOrder,
                relaxedPhonetics = true,
                extendedAsrPhonetics,
            )
            if (fixed != sub) {
                result = result.replaceRange(sp.first, sp.last, fixed)
            }
        }
        return result
    }
}
