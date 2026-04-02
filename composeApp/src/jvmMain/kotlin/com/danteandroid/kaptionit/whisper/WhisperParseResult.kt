package com.danteandroid.transbee.whisper

import kotlinx.serialization.Serializable

@Serializable
data class WhisperParseResult(
    val segments: List<TranscriptSegment>,
    val whisperLanguage: String?,
)
