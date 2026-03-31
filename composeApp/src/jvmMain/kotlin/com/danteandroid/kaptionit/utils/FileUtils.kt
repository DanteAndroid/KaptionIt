package com.danteandroid.kaptionit.utils

import kaptionit.composeapp.generated.resources.Res
import kaptionit.composeapp.generated.resources.dialog_open_video_file
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.URI
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

fun pickVideoFileWithChooser(): File? {
    if (GraphicsEnvironment.isHeadless()) return null
    val holder = arrayOfNulls<File>(1)
    SwingUtilities.invokeAndWait {
        val title = JvmResourceStrings.text(Res.string.dialog_open_video_file)
        val fd = FileDialog(null as Frame?, title, FileDialog.LOAD)
        fd.isMultipleMode = false
        fd.isVisible = true
        val name = fd.file
        val dir = fd.directory
        if (name != null && dir != null) {
            val f = File(dir, name)
            if (f.isFile) holder[0] = f
        }
    }
    return holder[0]
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
