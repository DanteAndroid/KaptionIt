package com.danteandroid.transbee.translate

import com.danteandroid.transbee.whisper.TranscriptSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TranslationServiceContextTest {

    @Test
    fun `buildSubtitleContext includes neighbors outside chunk boundaries`() {
        val segments = listOf(
            TranscriptSegment(0.0, 1.0, "line-0"),
            TranscriptSegment(1.0, 2.0, "line-1"),
            TranscriptSegment(2.0, 3.0, "line-2"),
            TranscriptSegment(3.0, 4.0, "line-3"),
            TranscriptSegment(4.0, 5.0, "line-4"),
        )

        val context = TranslationService.buildSubtitleContext(segments, 2)

        assertEquals(
            LlmSubtitleContext(
                prev2 = "line-0",
                prev = "line-1",
                next = "line-3",
                next2 = "line-4",
            ),
            context,
        )
    }

    @Test
    fun `buildLlmCacheKey changes when subtitle context changes`() {
        val first = LlmSubtitleContext(prev = "A", next = "B")
        val second = LlmSubtitleContext(prev = "X", next = "Y")

        assertNotEquals(
            TranslationService.buildLlmCacheKey("Right", first),
            TranslationService.buildLlmCacheKey("Right", second),
        )
    }
}
