package com.danteandroid.transbee.whisper

import kotlinx.serialization.json.Json

object WhisperJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun offsetMsToSec(fromMs: Long?, toMs: Long?): Pair<Double?, Double?> =
        Pair(fromMs?.let { it / 1000.0 }, toMs?.let { it / 1000.0 })

    private fun piecesFromWhisperWords(words: List<WhisperWordJson>): List<TranscriptWordPiece> =
        words.filter { it.word.isNotBlank() }.map { w ->
            TranscriptWordPiece(
                text = w.word.trim(),
                startSec = w.start,
                endSec = w.end,
                probability = w.probability,
            )
        }

    private fun piecesFromWhisperTokens(tokens: List<WhisperTokenJson>): List<TranscriptWordPiece> =
        tokens.mapNotNull { t ->
            val w = t.text.trim()
            if (w.isEmpty()) return@mapNotNull null
            if (w.startsWith('[') && w.endsWith(']')) return@mapNotNull null
            val (sSec, eSec) = offsetMsToSec(t.offsets?.from, t.offsets?.to)
            TranscriptWordPiece(
                text = w,
                startSec = sSec,
                endSec = eSec,
                probability = t.probability,
            )
        }

    fun parseResult(jsonText: String): WhisperParseResult {
        val root = json.decodeFromString<WhisperJsonRoot>(jsonText)
        val lang = root.language?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.result?.language?.trim()?.takeIf { it.isNotEmpty() }
        val raw = root.segments.orEmpty()
        val segments = when {
            raw.isNotEmpty() -> raw.map { s ->
                val words = s.words.orEmpty().filter { it.word.isNotBlank() }
                val fromWords = piecesFromWhisperWords(words)
                val fromTokens = piecesFromWhisperTokens(s.tokens.orEmpty())
                val pieces = when {
                    fromWords.isNotEmpty() -> fromWords
                    else -> fromTokens
                }
                val plain = if (pieces.isNotEmpty()) {
                    pieces.joinToString(" ") { it.text }.trim()
                } else {
                    s.text.trim()
                }
                TranscriptSegment(
                    startSec = s.startSec(),
                    endSec = s.endSec(),
                    text = plain,
                    wordPieces = pieces.takeIf { it.isNotEmpty() },
                )
            }
            !root.transcription.isNullOrEmpty() -> root.transcription.map { item ->
                val fromMs = item.offsets?.from
                val toMs = item.offsets?.to
                val startSec = when {
                    fromMs != null -> fromMs / 1000.0
                    else -> 0.0
                }
                val endSec = when {
                    toMs != null -> toMs / 1000.0
                    else -> startSec
                }
                val fromTokens = piecesFromWhisperTokens(item.tokens.orEmpty())
                val plain = item.text.trim()
                TranscriptSegment(
                    startSec = startSec,
                    endSec = endSec,
                    text = plain,
                    wordPieces = fromTokens.takeIf { it.isNotEmpty() },
                )
            }
            !root.text.isNullOrBlank() -> listOf(
                TranscriptSegment(
                    startSec = 0.0,
                    endSec = 0.0,
                    text = root.text.trim(),
                ),
            )
            else -> emptyList()
        }
        val nonBlank = segments.filter { it.text.isNotBlank() }
        return WhisperParseResult(segments = nonBlank, whisperLanguage = lang)
    }

    fun parseSegments(jsonText: String): List<TranscriptSegment> = parseResult(jsonText).segments
}
