package com.danteandroid.kaptionit.utils

object OsUtils {
    private val osName: String = System.getProperty("os.name")?.lowercase().orEmpty()

    fun isMacOs(): Boolean = osName.contains("mac") || osName.contains("darwin")
    
    fun isWindows(): Boolean = osName.contains("win")

    fun revealInFileBrowser(file: java.io.File) {
        val revealed = when {
            isMacOs() -> runCatching {
                Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
            }.isSuccess

            isWindows() -> runCatching {
                Runtime.getRuntime().exec(arrayOf("explorer", "/select,${file.absolutePath}"))
            }.isSuccess

            else -> false
        }
        if (!revealed) {
            runCatching { java.awt.Desktop.getDesktop().open(file.parentFile ?: file) }
        }
    }
}
