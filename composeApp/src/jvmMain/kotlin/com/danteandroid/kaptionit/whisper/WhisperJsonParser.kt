package com.danteandroid.transbee.whisper

import kotlinx.serialization.json.Json

object WhisperJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseResult(jsonText: String): WhisperParseResult {
        val root = json.decodeFromString<WhisperJsonRoot>(jsonText)
        val lang = root.language?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.result?.language?.trim()?.takeIf { it.isNotEmpty() }
        val raw = root.segments.orEmpty()
        val segments = when {
            raw.isNotEmpty() -> raw.map { s ->
                TranscriptSegment(
                    startSec = s.startSec(),
                    endSec = s.endSec(),
                    text = s.text.trim(),
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
                TranscriptSegment(
                    startSec = startSec,
                    endSec = endSec,
                    text = item.text.trim(),
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
