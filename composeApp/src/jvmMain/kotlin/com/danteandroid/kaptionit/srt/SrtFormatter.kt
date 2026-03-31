package com.danteandroid.kaptionit.srt

import com.danteandroid.kaptionit.whisper.TranscriptSegment

object SrtFormatter {

    fun buildBilingual(
        segments: List<TranscriptSegment>,
        translations: List<String>,
    ): String {
        require(segments.size == translations.size) {
            "segments=${segments.size}, translations=${translations.size}"
        }
        val sb = StringBuilder()
        segments.forEachIndexed { index, seg ->
            val n = index + 1
            sb.append(n).append('\n')
            sb.append(formatTimestamp(seg.startSec)).append(" --> ")
                .append(formatTimestamp(seg.endSec)).append('\n')
            sb.append(translations[index].trim()).append('\n')
            sb.append(seg.text).append('\n')
            sb.append('\n')
        }
        return sb.toString()
    }

    fun buildTargetOnly(
        segments: List<TranscriptSegment>,
        translations: List<String>,
    ): String {
        require(segments.size == translations.size) {
            "segments=${segments.size}, translations=${translations.size}"
        }
        val sb = StringBuilder()
        segments.forEachIndexed { index, seg ->
            val n = index + 1
            sb.append(n).append('\n')
            sb.append(formatTimestamp(seg.startSec)).append(" --> ")
                .append(formatTimestamp(seg.endSec)).append('\n')
            sb.append(translations[index].trim()).append('\n')
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun formatTimestamp(seconds: Double): String {
        val totalMs = (seconds.coerceAtLeast(0.0) * 1000.0).toLong()
        val h = totalMs / 3_600_000
        val m = (totalMs % 3_600_000) / 60_000
        val s = (totalMs % 60_000) / 1_000
        val ms = (totalMs % 1_000).toInt()
        return "%02d:%02d:%02d,%03d".format(h, m, s, ms)
    }
}
