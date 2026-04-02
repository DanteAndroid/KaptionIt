package com.danteandroid.transbee.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset

object ProcessRunner {

    suspend fun run(
        command: List<String>,
        onStdoutLine: (String) -> Unit = {},
        charset: Charset = Charsets.UTF_8,
    ): Int = withContext(Dispatchers.IO) {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        val process = try {
            pb.start()
        } catch (e: IOException) {
            val program = command.firstOrNull().orEmpty()
            val msg = e.message.orEmpty()
            val isNotFound = msg.contains("error=2") || msg.contains("ENOENT") ||
                    msg.contains("CreateProcess error=2", ignoreCase = true) ||
                    msg.contains("No such file", ignoreCase = true) ||
                    msg.contains("not found", ignoreCase = true)
            if (isNotFound) {
                throw IOException(
                    "执行组件缺失「$program」。请确保相关依赖或引擎完整无缺，或尝试重启应用解决。($msg)",
                    e,
                )
            }
            if (msg.contains("Permission denied", ignoreCase = true) || msg.contains("error=13", ignoreCase = true)) {
                throw IOException(
                    "没有权限运行「$program」。在 macOS 上可能被隔离或没有可执行权限，请参考相关说明授权。($msg)",
                    e,
                )
            }
            throw IOException("启动引擎失败：$msg", e)
        }
        try {
            process.inputStream.bufferedReader(charset).use { reader ->
                reader.lineSequence().forEach { line ->
                    ensureActive()
                    onStdoutLine(line)
                }
            }
            ensureActive()
            process.waitFor()
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor()
            }
        }
    }
}