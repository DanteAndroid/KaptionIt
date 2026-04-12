package com.danteandroid.transbee.utils

object OsUtils {
    private val osName: String = System.getProperty("os.name")?.lowercase().orEmpty()

    fun isMacOs(): Boolean = osName.contains("mac") || osName.contains("darwin")

    /** Windows 10/11 等均为 "windows …"，用 win 判断即可 */
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

    fun openFile(file: java.io.File) {
        runCatching {
            if (isMacOs()) {
                Runtime.getRuntime().exec(arrayOf("open", file.absolutePath))
            } else if (isWindows()) {
                Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "", file.absolutePath))
            } else {
                java.awt.Desktop.getDesktop().open(file)
            }
        }
    }
}
