package transbee.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateBundledNativeDistributionPathTask : DefaultTask() {

    @get:Input
    abstract val nativeDistributionPath: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val abs = File(nativeDistributionPath.get()).canonicalFile.absolutePath
        val literal = kotlinStringLiteralForBundledPath(abs)
        val f = outputFile.get().asFile
        f.parentFile.mkdirs()
        f.writeText(
            buildString {
                appendLine("package com.danteandroid.transbee.bundled")
                appendLine()
                appendLine("internal object BundledNativeDistributionPath {")
                appendLine("    const val ABSOLUTE_PATH: String = $literal")
                appendLine("}")
                appendLine()
            },
        )
    }

    private fun kotlinStringLiteralForBundledPath(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '$' -> sb.append("\\$")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
