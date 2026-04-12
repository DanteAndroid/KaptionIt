package com.danteandroid.transbee.translate

/**
 * 专业词汇工具：规范化词汇列表、生成 LLM 提示词块。
 */
object GlossaryLoader {
    data class TermMapping(
        val source: String,
        val target: String,
    )

    fun normalizeTerms(terms: List<String>): List<String> =
        terms.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }

    fun normalizeMappings(mappings: List<TermMapping>): List<TermMapping> =
        mappings.mapNotNull { item ->
            val source = item.source.trim()
            val target = item.target.trim()
            if (source.isEmpty() || target.isEmpty()) null else TermMapping(source = source, target = target)
        }.distinctBy { it.source.lowercase() to it.target }
            .sortedByDescending { it.source.length }

    fun toForcedTranslationPromptBlock(mappings: List<TermMapping>): String {
        if (mappings.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("【专业词汇——以下词汇必须使用指定译法】")
        mappings.forEach { item ->
            sb.appendLine("• ${item.source} -> ${item.target}")
        }
        return sb.toString()
    }
}
