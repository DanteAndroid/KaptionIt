package com.danteandroid.kaptionit.ui

data class ModelDownloadUiState(
    val active: Boolean = false,
    val fileName: String = "",
    val progress: Float = 0f,
    val receivedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val message: String = "",
    val error: String? = null,
    val skippedExisting: Boolean = false,
)
