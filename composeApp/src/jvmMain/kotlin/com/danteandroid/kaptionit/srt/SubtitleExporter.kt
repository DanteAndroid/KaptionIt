package com.danteandroid.kaptionit.srt

import com.danteandroid.kaptionit.whisper.TranscriptSegment

object SubtitleExporter {

    data class ExportFile(
        val nameSuffix: String? = null,
        val body: String,
    )

    fun exportFiles(
        segments: List<TranscriptSegment>,
        translations: List<String>,
        format: String,
        subtitleOutputs: Set<String>,
        targetSuffix: String = "_zh",
    ): List<ExportFile> {
        val sourceTexts = segments.map { it.text }
        val normalized = subtitleOutputs.map { it.lowercase() }.toSet()
        if (normalized.isEmpty()) {
            return listOf(ExportFile(body = buildTargetOnly(segments, sourceTexts, format)))
        }
        val files = mutableListOf<ExportFile>()
        if (normalized.contains("source")) {
            files.add(ExportFile(body = buildTargetOnly(segments, sourceTexts, format)))
        }
        if (normalized.contains("target")) {
            files.add(ExportFile(nameSuffix = targetSuffix, body = buildTargetOnly(segments, translations, format)))
        }
        if (normalized.contains("bilingual_single")) {
            files.add(ExportFile(nameSuffix = "_bi", body = buildBilingual(segments, translations, format)))
        }
        if (files.isEmpty()) {
            files.add(ExportFile(body = buildTargetOnly(segments, sourceTexts, format)))
        }
        return files
    }

    private fun buildTargetOnly(
        segments: List<TranscriptSegment>,
        lines: List<String>,
        format: String,
    ): String = when (format.lowercase()) {
        "vtt" -> buildVtt(segments, lines, bilingual = false)
        "txt" -> buildTxt(segments, lines, bilingual = false)
        else -> SrtFormatter.buildTargetOnly(segments, lines)
    }

    private fun buildBilingual(
        segments: List<TranscriptSegment>,
        translations: List<String>,
        format: String,
    ): String = when (format.lowercase()) {
        "vtt" -> buildVtt(segments, translations, bilingual = true)
        "txt" -> buildTxt(segments, translations, bilingual = true)
        else -> SrtFormatter.buildBilingual(segments, translations)
    }

    private fun buildVtt(
        segments: List<TranscriptSegment>,
        translations: List<String>,
        bilingual: Boolean,
    ): String {
        val sb = StringBuilder("WEBVTT\n\n")
        segments.forEachIndexed { index, seg ->
            sb.append(formatVttTimestamp(seg.startSec)).append(" --> ")
                .append(formatVttTimestamp(seg.endSec)).append('\n')
            if (bilingual) {
                sb.append(translations[index].trim()).append('\n')
                sb.append(seg.text).append('\n')
            } else {
                sb.append(translations[index].trim()).append('\n')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun buildTxt(
        segments: List<TranscriptSegment>,
        translations: List<String>,
        bilingual: Boolean,
    ): String = buildString {
        segments.forEachIndexed { index, seg ->
            if (bilingual) {
                append(translations[index].trim()).append('\n')
                append(seg.text).append('\n')
            } else {
                append(translations[index].trim()).append('\n')
            }
            append('\n')
        }
    }

    private fun formatVttTimestamp(seconds: Double): String {
        val totalMs = (seconds.coerceAtLeast(0.0) * 1000.0).toLong()
        val h = totalMs / 3_600_000
        val m = (totalMs % 3_600_000) / 60_000
        val s = (totalMs % 60_000) / 1_000
        val ms = (totalMs % 1_000).toInt()
        return "%02d:%02d:%02d.%03d".format(h, m, s, ms)
    }
}
