package com.danteandroid.transbee.whisper

object WhisperCliArgs {

    /** whisper-cli `-vmsd`：VAD 单段语音最长秒数，超过则自动切分 */
    const val VAD_MAX_SPEECH_DURATION_S: Float = 28f

    /** whisper-cli `-ml`：单条最大字符数（配合 `-sow`） */
    const val SEGMENT_MAX_CHARS: Int = 260

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
