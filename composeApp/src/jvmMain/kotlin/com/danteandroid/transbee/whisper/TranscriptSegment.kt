package com.danteandroid.transbee.whisper

import kotlinx.serialization.Serializable

@Serializable
data class TranscriptWordPiece(
    val text: String,
    val startSec: Double? = null,
    val endSec: Double? = null,
    val probability: Double? = null,
)

@Serializable
data class TranscriptSegment(
    val startSec: Double,
    val endSec: Double,
    val text: String,
    val wordPieces: List<TranscriptWordPiece>? = null,
) {
    fun textForSourceSubtitle(): String = text.trim()
}

