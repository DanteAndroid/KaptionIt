package com.danteandroid.transbee.whisper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhisperJsonRoot(
    val segments: List<WhisperSegmentJson>? = null,
    val text: String? = null,
    val language: String? = null,
    val transcription: List<WhisperTranscriptionItemJson>? = null,
    val result: WhisperResultJson? = null,
)

@Serializable
data class WhisperResultJson(
    val language: String? = null,
)

@Serializable
data class WhisperTokenJson(
    val text: String,
    val offsets: WhisperOffsetsJson? = null,
    val id: Int? = null,
    @SerialName("p") val probability: Double? = null,
)

@Serializable
data class WhisperTranscriptionItemJson(
    val text: String,
    val offsets: WhisperOffsetsJson? = null,
    val tokens: List<WhisperTokenJson>? = null,
)

@Serializable
data class WhisperOffsetsJson(
    val from: Long? = null,
    val to: Long? = null,
)

@Serializable
data class WhisperWordJson(
    val word: String,
    val start: Double? = null,
    val end: Double? = null,
    val probability: Double? = null,
)

@Serializable
data class WhisperSegmentJson(
    val text: String,
    val start: Double? = null,
    val end: Double? = null,
    @SerialName("t0") val t0: Double? = null,
    @SerialName("t1") val t1: Double? = null,
    val words: List<WhisperWordJson>? = null,
    val tokens: List<WhisperTokenJson>? = null,
) {
    fun startSec(): Double = start ?: t0 ?: 0.0
    fun endSec(): Double = end ?: t1 ?: startSec()
}
