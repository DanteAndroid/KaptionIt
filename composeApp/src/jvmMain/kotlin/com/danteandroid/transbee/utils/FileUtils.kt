package com.danteandroid.transbee.utils

import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.dialog_open_video_file
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.swing.SwingUtilities

/** 解析拖放路径（file: URI 或绝对路径）为 [File]，不存在则返回 null */
fun fileFromDragDropPath(pathOrUri: String): File? {
    val raw = pathOrUri.trim()
    if (raw.isEmpty()) return null
    val file = try {
        if (raw.startsWith("file:")) File(URI(raw)) else File(raw)
    } catch (_: Exception) {
        File(raw)
    }
    return file.takeIf { it.isFile }
}

fun pickFilesWithChooser(): List<File> {
    if (GraphicsEnvironment.isHeadless()) return emptyList()
    val holder = mutableListOf<File>()
    SwingUtilities.invokeAndWait {
        val title = JvmResourceStrings.text(Res.string.dialog_open_video_file)
        val fd = FileDialog(null as Frame?, title, FileDialog.LOAD)
        fd.isMultipleMode = true
        fd.isVisible = true
        fd.files?.let { holder.addAll(it) }
    }
    return holder
}

/** 从 MinerU 的 ZIP 结果包中提取 .md 文件到目标位置 */
fun extractMdFromZip(zipFile: File, destMdFile: File): File {
    ZipFile(zipFile).use { zip ->
        val mdEntry = zip.entries().asSequence().find { it.name.endsWith(".md") }
            ?: error("ZIP 包中未找到 Markdown 文件")

        zip.getInputStream(mdEntry).use { input ->
            Files.copy(input, destMdFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    return destMdFile
}

fun Long.toReadableByteSize(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun File.subtitleOutputFile(format: String, nameSuffix: String? = null): File {
    val parent = parentFile ?: absoluteFile.parentFile
    val baseName = name.substringBeforeLast('.')
    val dir = parent ?: File(".")
    val ext = when (format.lowercase()) {
        "vtt" -> "vtt"
        "txt" -> "txt"
        else -> "srt"
    }
    val stem = if (nameSuffix.isNullOrEmpty()) baseName else "$baseName$nameSuffix"
    return File(dir, "$stem.$ext")
}
