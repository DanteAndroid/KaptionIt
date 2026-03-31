package com.danteandroid.kaptionit.whisper

import kotlinx.serialization.Serializable

@Serializable
data class WhisperParseResult(
    val segments: List<TranscriptSegment>,
    val whisperLanguage: String?,
)
