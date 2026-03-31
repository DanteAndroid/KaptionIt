package com.danteandroid.kaptionit.whisper

object WhisperCliArgs {

    /**
     * whisper.cpp `-t`：在 Apple Silicon 等机器上略少于全部逻辑核，避免与系统/UI 抢占；
     * 上限 8 与常见 whisper 推荐一致。
     */
    fun threadCount(): Int {
        val p = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return when {
            p >= 12 -> 8
            p >= 8 -> 6
            p >= 6 -> 5
            else -> p.coerceIn(2, 4)
        }
    }
}
