package com.danteandroid.transbee.utils

import transbee.composeapp.generated.resources.Res
import transbee.composeapp.generated.resources.dialog_open_video_file
import transbee.composeapp.generated.resources.err_zip_md_missing
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.swing.SwingUtilities
import kotlin.text.RegexOption

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
    val showDialog: () -> Unit = {
        val title = JvmResourceStrings.text(Res.string.dialog_open_video_file)
        val fd = FileDialog(null as Frame?, title, FileDialog.LOAD)
        fd.isMultipleMode = true
        fd.isVisible = true
        fd.files?.let { holder.addAll(it) }
        Unit
    }
    if (SwingUtilities.isEventDispatchThread()) showDialog() else SwingUtilities.invokeAndWait(showDialog)
    return holder
}

/**
 * 从 MinerU 的 ZIP 结果包中提取 Markdown：同时解压与 full.md 同级的目录（如 images/），
 * 并重写文中对 images/ 的相对引用，使与输出 .md 同目录下的资源文件夹一致。
 */
fun extractMdFromZip(zipFile: File, destMdFile: File): File {
    val assetDirName = "${destMdFile.nameWithoutExtension}.mineru.assets"
    val assetsDir = File(destMdFile.parentFile, assetDirName)
    ZipFile(zipFile).use { zip ->
        val all = zip.entries().asSequence().filter { !it.isDirectory }.toList()
        val mdFiles = all.filter { it.name.endsWith(".md", ignoreCase = true) }
        val mdEntry = mdFiles.firstOrNull {
            it.name.equals("full.md", ignoreCase = true) ||
                it.name.endsWith("/full.md", ignoreCase = true)
        } ?: mdFiles.firstOrNull() ?: error(JvmResourceStrings.text(Res.string.err_zip_md_missing))

        val mdZipPath = mdEntry.name.replace('\\', '/')
        val folderPrefix =
            if ('/' in mdZipPath) mdZipPath.substringBeforeLast('/') + "/" else ""

        if (assetsDir.exists()) assetsDir.deleteRecursively()
        assetsDir.mkdirs()
        val baseDir = assetsDir.canonicalFile

        val mdFileNameInZip = mdZipPath.substringAfterLast('/')
        for (entry in all) {
            val name = entry.name.replace('\\', '/')
            val include = when {
                folderPrefix.isNotEmpty() -> name.startsWith(folderPrefix)
                else -> name == mdZipPath || name.startsWith("images/", ignoreCase = true)
            }
            if (!include || name == mdZipPath) continue
            val relative = name.removePrefix(folderPrefix)
            val outFile = File(assetsDir, relative)
            val canonical = outFile.canonicalFile
            if (!canonical.path.startsWith(baseDir.path)) continue
            canonical.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                Files.copy(input, canonical.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        val mdText = zip.getInputStream(mdEntry).use { stream ->
            stream.readBytes().toString(StandardCharsets.UTF_8)
        }
        destMdFile.writeText(rewriteMinerUImagePaths(mdText, assetDirName), StandardCharsets.UTF_8)
    }
    return destMdFile
}

private val mineruMdImageRef =
    Regex("""(\]\()(\./)?images/""", setOf(RegexOption.IGNORE_CASE))

private val mineruHtmlImgSrc =
    Regex("""(src\s*=\s*["'])(\./)?images/""", setOf(RegexOption.IGNORE_CASE))

private fun rewriteMinerUImagePaths(md: String, assetDirName: String): String {
    var s = mineruMdImageRef.replace(md) { m ->
        m.groupValues[1] + m.groupValues[2] + assetDirName + "/images/"
    }
    s = mineruHtmlImgSrc.replace(s) { m ->
        m.groupValues[1] + m.groupValues[2] + assetDirName + "/images/"
    }
    return s
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
