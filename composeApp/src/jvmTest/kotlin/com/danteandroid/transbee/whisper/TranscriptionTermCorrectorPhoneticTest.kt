package com.danteandroid.transbee.whisper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptionTermCorrectorPhoneticTest {

    @Test
    fun soundsLikeStrict_falseForUnrelatedLongWords() {
        assertFalse(TranscriptionTermCorrector.soundsLikeStrictForTest("ridiculous", "equivocate"))
        assertFalse(TranscriptionTermCorrector.soundsLikeStrictForTest("photography", "equivocate"))
        assertFalse(TranscriptionTermCorrector.soundsLikeStrictForTest("absolutely", "equivocate"))
    }

    @Test
    fun phoneticMatch_extended_falseForUnrelatedSingleWords() {
        assertFalse(TranscriptionTermCorrector.phoneticMatchForTest("ridiculous", "equivocate", true))
    }

    @Test
    fun phoneticMatch_extended_falseForMraFalsePositives() {
        assertFalse(TranscriptionTermCorrector.phoneticMatchForTest("I could", "one ahead", true))
        assertFalse(TranscriptionTermCorrector.phoneticMatchForTest("that", "billet", true))
        assertFalse(TranscriptionTermCorrector.phoneticMatchForTest("a quiver", "equivocate", true))
        assertFalse(TranscriptionTermCorrector.phoneticMatchForTest("K at", "billet", true))
    }

    @Test
    fun soundsLikeStrict_falseForShortStrings() {
        assertFalse(TranscriptionTermCorrector.soundsLikeStrictForTest("the", "them"))
        assertFalse(TranscriptionTermCorrector.soundsLikeStrictForTest("cat", "dog"))
    }

    @Test
    fun soundsLikeStrict_trueForCaseOrCollapsedSpaces() {
        assertTrue(TranscriptionTermCorrector.soundsLikeStrictForTest("equivocate", "EQUIVOCATE"))
        assertTrue(TranscriptionTermCorrector.soundsLikeStrictForTest("new york", "newyork"))
    }

    /** 常见 Metaphone 近音：Stephen / Steven（主或次码会对齐） */
    @Test
    fun soundsLikeStrict_trueForKnownMetaphoneNeighbors() {
        assertTrue(TranscriptionTermCorrector.soundsLikeStrictForTest("Stephen", "Steven"))
    }

    @Test
    fun phoneticMatch_quiverK_to_equivocate_whenExtended() {
        assertTrue(TranscriptionTermCorrector.phoneticMatchForTest("quiver K", "equivocate", true))
    }

    @Test
    fun replaceGlossarySpans_relaxedExtended_replacesQuiverK() {
        val terms = listOf("equivocate")
        val out = TranscriptionTermCorrector.replaceGlossarySpansForTest(
            "I could use a quiver K at that point",
            terms,
            relaxedPhonetics = true,
            extendedAsrPhonetics = true,
        )
        assertTrue(out.contains("equivocate", ignoreCase = true))
        assertFalse(Regex("""quiver\s+K""", RegexOption.IGNORE_CASE).containsMatchIn(out))
    }

    @Test
    fun replaceGlossarySpans_relaxedExtended_multiTerm_onlyQuiverKToEquivocate() {
        val line = "I could use a quiver K at that point, anything I wanted."
        val out = TranscriptionTermCorrector.replaceGlossarySpansForTest(
            line,
            listOf("one ahead", "equivocate", "billet"),
            relaxedPhonetics = true,
            extendedAsrPhonetics = true,
        )
        assertEquals(
            "I could use a equivocate at that point, anything I wanted.",
            out,
        )
    }

    @Test
    fun replaceGlossarySpans_strict_noPhoneticChange() {
        val terms = listOf("equivocate")
        val out = TranscriptionTermCorrector.replaceGlossarySpansForTest(
            "ridiculous answer",
            terms,
            relaxedPhonetics = false,
        )
        assertEquals("ridiculous answer", out)
    }

    @Test
    fun replaceGlossarySpans_relaxed_doesNotReplaceUnrelated() {
        val terms = listOf("equivocate")
        val out = TranscriptionTermCorrector.replaceGlossarySpansForTest(
            "that was ridiculous",
            terms,
            relaxedPhonetics = true,
        )
        assertEquals("that was ridiculous", out)
    }

    @Test
    fun replaceGlossarySpans_relaxed_replacesExact() {
        val terms = listOf("equivocate")
        val out = TranscriptionTermCorrector.replaceGlossarySpansForTest(
            "use equivocate here",
            terms,
            relaxedPhonetics = true,
        )
        assertEquals("use equivocate here", out)
    }
}
