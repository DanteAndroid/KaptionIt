package com.danteandroid.kaptionit.whisper

import java.io.File

data class WhisperModelOption(
    val id: String,
    val label: String,
    val fileName: String,
) {
    val url: String
        get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"
}

fun WhisperModelOption.modelFile(): File =
    File(WhisperModelPaths.modelsDirectory(), fileName)

fun WhisperModelOption.isDownloaded(): Boolean =
    modelFile().isFile

object WhisperModelCatalog {
    val presetsMain: List<WhisperModelOption> = listOf(
        WhisperModelOption("tiny", "tiny（75MB）", "ggml-tiny.bin"),
        WhisperModelOption("base", "base（148MB，推荐）", "ggml-base.bin"),
        WhisperModelOption("small", "small（488MB）", "ggml-small.bin"),
        WhisperModelOption("medium", "medium（1.5GB）", "ggml-medium.bin"),
        WhisperModelOption("large-v3-turbo", "large-v3-turbo（1.6GB）", "ggml-large-v3-turbo.bin"),
        WhisperModelOption("large-v3", "large-v3（3GB）", "ggml-large-v3.bin"),
    )

    val presetsEnglishOnly: List<WhisperModelOption> = listOf(
        WhisperModelOption("tiny.en", "tiny.en（75MB）", "ggml-tiny.en.bin"),
        WhisperModelOption("base.en", "base.en（148MB）", "ggml-base.en.bin"),
        WhisperModelOption("small.en", "small.en（488MB）", "ggml-small.en.bin"),
        WhisperModelOption("medium.en", "medium.en（1.5GB）", "ggml-medium.en.bin"),
    )

    val presets: List<WhisperModelOption> = presetsMain + presetsEnglishOnly
}
