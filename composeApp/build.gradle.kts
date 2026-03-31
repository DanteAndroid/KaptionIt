import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import whisperit.tasks.GenerateBundledNativeDistributionPathTask

private val whisperCppTag = "v1.8.4"
private val whisperWinZipUrl =
    "https://github.com/ggml-org/whisper.cpp/releases/download/$whisperCppTag/whisper-bin-x64.zip"

private fun osName(): String = System.getProperty("os.name").orEmpty().lowercase()

private fun isMacOs(): Boolean = osName().contains("mac")

private fun isWindowsOs(): Boolean = osName().contains("windows")

private fun isUnixLikeDesktop(): Boolean =
    isMacOs() || (osName().contains("linux") && !osName().contains("android"))

private fun findCmakeExecutable(): File {
    val candidates = listOf(
        "/opt/homebrew/bin/cmake",
        "/usr/local/bin/cmake",
        "/usr/bin/cmake",
    )
    for (p in candidates) {
        val f = File(p)
        if (f.isFile && f.canExecute()) return f
    }
    val path = System.getenv("PATH") ?: ""
    for (dir in path.split(File.pathSeparator)) {
        if (dir.isEmpty()) continue
        val f = File(dir, "cmake")
        if (f.isFile && f.canExecute()) return f
    }
    throw GradleException(
        "未找到 cmake，无法自动编译 whisper-cli。请先安装 CMake（macOS：brew install cmake），" +
            "并确保 Xcode 命令行工具可用（xcode-select --install），然后重新执行构建。",
    )
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/whisperit/kotlin"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

val gitCommitCount = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim()
}.getOrElse { "0" }
val computedPackageVersion = "1.0.$gitCommitCount"

val generateBundledNativeDistributionPath =
    tasks.register<GenerateBundledNativeDistributionPathTask>("generateBundledNativeDistributionPath") {
        group = "build"
        nativeDistributionPath.set(
            layout.projectDirectory.dir("native-distribution/common").asFile.canonicalFile.absolutePath,
        )
        outputFile.set(
            layout.buildDirectory.file(
                "generated/whisperit/kotlin/com/danteandroid/whisperit/bundled/BundledNativeDistributionPath.kt",
            ),
        )
    }

val generateBuildConfig = tasks.register("generateBuildConfig") {
    val outDir = layout.buildDirectory.dir("generated/whisperit/kotlin/com/danteandroid/whisperit/bundled")
    outputs.dir(outDir)
    val versionText = computedPackageVersion
    doLast {
        val f = outDir.get().file("BuildConfig.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("""
            package com.danteandroid.whisperit.bundled
            
            object BuildConfig {
                const val APP_VERSION = "$versionText"
            }
        """.trimIndent())
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateBundledNativeDistributionPath, generateBuildConfig)
}

val buildAppleTranslateMac = tasks.register<Exec>("buildAppleTranslateMac") {
    group = "build"
    description = "在 macOS 上编译 AppleTranslate（swift build -c release）"
    notCompatibleWithConfigurationCache("调用 swift build")
    val appleTranslateDir = rootProject.layout.projectDirectory.dir("native/AppleTranslate").asFile
    workingDir = appleTranslateDir
    commandLine("swift", "build", "-c", "release")
    onlyIf {
        isMacOs() && File(appleTranslateDir, "Package.swift").isFile
    }
}

val syncAppleTranslateBundle = tasks.register("syncAppleTranslateBundle") {
    group = "build"
    description = "将 AppleTranslate 复制到 composeApp/native-distribution/common（仅 macOS）"
    dependsOn(buildAppleTranslateMac)
    notCompatibleWithConfigurationCache("复制 AppleTranslate 可执行文件")
    val rootDir = rootProject.layout.projectDirectory.asFile
    val outDir = layout.projectDirectory.dir("native-distribution/common").asFile
    onlyIf { isMacOs() }
    doLast {
        val candidates = listOf(
            File(rootDir, "native/AppleTranslate/.build/arm64-apple-macosx/release/AppleTranslate"),
            File(rootDir, "native/AppleTranslate/.build/x86_64-apple-macosx/release/AppleTranslate"),
            File(rootDir, "native/AppleTranslate/.build/release/AppleTranslate"),
        )
        val fromFile = candidates.firstOrNull { it.isFile }
            ?: throw GradleException(
                "未找到 AppleTranslate 可执行文件（已尝试 swift build）。请确认 native/AppleTranslate 可正常编译，" +
                    "且已安装 Xcode 命令行工具（xcode-select --install）。",
            )
        outDir.mkdirs()
        val dest = File(outDir, "AppleTranslate")
        Files.copy(fromFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        dest.setExecutable(true, false)
    }
}

val repoRootPath = rootProject.layout.projectDirectory.asFile.absolutePath
val composeNativeDistPath = layout.projectDirectory.dir("native-distribution").asFile.absolutePath

/** 仅当目标不存在时下载；已存在则跳过，无需单独 fetch 任务。 */
private fun ensureBundledFfmpegMac(repoRoot: String) {
    val dest = File(repoRoot, "native/bundled/ffmpeg")
    if (dest.isFile) return
    if (!isMacOs()) return
    val zipFile = File(layout.buildDirectory.dir("tmp/bundled").get().asFile, "ffmpeg.zip")
    zipFile.parentFile.mkdirs()
    dest.parentFile.mkdirs()
    URI.create("https://evermeet.cx/ffmpeg/getrelease/zip").toURL().openStream().use { inp ->
        Files.copy(inp, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    ZipFile(zipFile).use { zip ->
        val entry = zip.getEntry("ffmpeg")
            ?: throw GradleException("FFmpeg 压缩包内缺少 ffmpeg 文件")
        zip.getInputStream(entry).use { stream ->
            Files.copy(stream, dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    zipFile.delete()
    dest.setExecutable(true, false)
    println("已下载 FFmpeg → ${dest.absolutePath}")
}

private fun ensureBundledFfmpegWin(repoRoot: String) {
    val exe = File(repoRoot, "native/bundled/ffmpeg.exe")
    if (exe.isFile) return
    if (!isWindowsOs()) return
    val zipFile = File(layout.buildDirectory.dir("tmp/bundled").get().asFile, "ffmpeg-win.zip")
    zipFile.parentFile.mkdirs()
    exe.parentFile.mkdirs()
    URI.create("https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip").toURL().openStream().use { inp ->
        Files.copy(inp, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    ZipFile(zipFile).use { zip ->
        var found = false
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (e.name.endsWith("bin/ffmpeg.exe")) {
                zip.getInputStream(e).use { stream ->
                    Files.copy(stream, exe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                found = true
                break
            }
        }
        if (!found) {
            throw GradleException("FFmpeg Windows 压缩包内缺少 bin/ffmpeg.exe 文件")
        }
    }
    zipFile.delete()
    exe.setExecutable(true, false)
    println("已下载 Windows FFmpeg → ${exe.absolutePath}")
}

private fun ensureBundledWhisperWin(repoRoot: String) {
    val exe = File(repoRoot, "native/bundled/whisper-win/whisper-cli.exe")
    if (exe.isFile) return
    if (!isWindowsOs()) return
    val outDir = File(repoRoot, "native/bundled/whisper-win")
    outDir.mkdirs()
    val zipFile = File(layout.buildDirectory.dir("tmp/bundled").get().asFile, "whisper-win.zip")
    zipFile.parentFile.mkdirs()
    URI.create(whisperWinZipUrl).toURL().openStream().use { inp ->
        Files.copy(inp, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    ZipFile(zipFile).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            val rel = e.name.removePrefix("Release/").removePrefix("release/")
            if (rel.isEmpty() || rel.endsWith("/")) continue
            val fileDest = File(outDir, rel)
            fileDest.parentFile?.mkdirs()
            zip.getInputStream(e).use { stream ->
                Files.copy(stream, fileDest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
    zipFile.delete()
    if (!exe.isFile) {
        throw GradleException("Whisper Windows 压缩包内缺少 whisper-cli.exe")
    }
    println("已解压 Whisper Windows 二进制 → ${outDir.absolutePath}")
}

val syncBundledFfmpeg = tasks.register("syncBundledFfmpeg") {
    group = "build"
    notCompatibleWithConfigurationCache("ensures FFmpeg then copies into native-distribution")
    doLast {
        val outDir = File(composeNativeDistPath, "common")
        outDir.mkdirs()
        if (isWindowsOs()) {
            ensureBundledFfmpegWin(repoRootPath)
            val src = File(repoRootPath, "native/bundled/ffmpeg.exe")
            if (!src.isFile) return@doLast
            val out = File(outDir, "ffmpeg.exe")
            src.copyTo(out, overwrite = true)
            out.setExecutable(true, false)
        } else {
            ensureBundledFfmpegMac(repoRootPath)
            val src = File(repoRootPath, "native/bundled/ffmpeg")
            if (!src.isFile) return@doLast
            val out = File(outDir, "ffmpeg")
            src.copyTo(out, overwrite = true)
            out.setExecutable(true, false)
        }
    }
}

val buildBundledWhisperCliUnix = tasks.register("buildBundledWhisperCliUnix") {
    group = "build"
    notCompatibleWithConfigurationCache("builds whisper-cli from whisper.cpp via CMake")
    onlyIf {
        isUnixLikeDesktop() &&
            !File(repoRootPath, "native/bundled/whisper-cli").isFile
    }
    doLast {
        val cmake = findCmakeExecutable().absolutePath
        val srcDir = File(repoRootPath, "native/bundled/.whisper-src")
        val buildDir = File(repoRootPath, "native/bundled/.whisper-build")
        val destBin = File(repoRootPath, "native/bundled/whisper-cli")
        if (!srcDir.isDirectory) {
            project.exec {
                commandLine(
                    "git",
                    "clone",
                    "--depth",
                    "1",
                    "--branch",
                    whisperCppTag,
                    "https://github.com/ggml-org/whisper.cpp.git",
                    srcDir.absolutePath,
                )
            }
        }
        buildDir.mkdirs()
        project.exec {
            commandLine(
                cmake,
                "-S",
                srcDir.absolutePath,
                "-B",
                buildDir.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
            )
        }
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        project.exec {
            commandLine(
                cmake,
                "--build",
                buildDir.absolutePath,
                "--target",
                "whisper-cli",
                "--parallel",
                cores.toString(),
            )
        }
        val built = File(buildDir, "bin/whisper-cli")
        if (!built.isFile) {
            throw GradleException("CMake 未生成 bin/whisper-cli，请确认已安装 Xcode 命令行工具（macOS）或 CMake 与 gcc（Linux）。")
        }
        Files.copy(built.toPath(), destBin.toPath(), StandardCopyOption.REPLACE_EXISTING)
        destBin.setExecutable(true, false)
        println("已编译 whisper-cli → ${destBin.absolutePath}")
    }
}

val syncBundledWhisperCli = tasks.register("syncBundledWhisperCli") {
    group = "build"
    dependsOn(buildBundledWhisperCliUnix)
    notCompatibleWithConfigurationCache("ensures whisper binaries then copies into native-distribution")
    doLast {
        ensureBundledWhisperWin(repoRootPath)
        val outRoot = File(composeNativeDistPath, "common")
        outRoot.mkdirs()
        when {
            isWindowsOs() -> {
                val srcDir = File(repoRootPath, "native/bundled/whisper-win")
                if (!srcDir.isDirectory) return@doLast
                val outDir = File(outRoot, "whisper-bin")
                outDir.mkdirs()
                srcDir.copyRecursively(outDir, overwrite = true)
            }
            else -> {
                val src = File(repoRootPath, "native/bundled/whisper-cli")
                if (!src.isFile) return@doLast
                val out = File(outRoot, "whisper-cli")
                Files.copy(src.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                out.setExecutable(true, false)
                println("已同步 whisper-cli → ${out.absolutePath}")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.danteandroid.whisperit.MainKt"
        jvmArgs("-Djava.net.useSystemProxies=true")

        nativeDistributions {
            modules("java.net.http")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KaptionIt"
            packageVersion = computedPackageVersion
            appResourcesRootDir.set(project.layout.projectDirectory.dir("native-distribution"))
            macOS {
                bundleID = "com.danteandroid.kaptionit"
                dockName = "KaptionIt" // 专门用于 Dock 栏显示的名字
                iconFile.set(project.file("icons/whisperit.icns"))
                entitlementsFile.set(project.file("entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
            <key>CFBundleLocalizations</key>
            <array>
                <string>zh</string>
                <string>zh_CN</string>
                <string>en</string>
            </array>
            <key>CFBundleDevelopmentRegion</key>
            <string>zh_CN</string>
        """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("icons/whisperit.ico"))
                shortcut = true
                menu = true
            }
            linux {
                iconFile.set(project.file("icons/whisperit_linux.png"))
            }
        }
    }
}

afterEvaluate {
    tasks.named("prepareAppResources") {
        dependsOn(
            syncAppleTranslateBundle,
            syncBundledFfmpeg,
            syncBundledWhisperCli,
        )
    }
    val nativeDistPath = layout.projectDirectory.dir("native-distribution/common").asFile.absolutePath
    val dockIconPath =
        layout.projectDirectory.file("src/jvmMain/composeResources/drawable/whisperit_app_icon.png").asFile.absolutePath
    fun JavaExec.configureComposeRunResources() {
        systemProperty("compose.application.resources.dir", nativeDistPath)
        if (isMacOs()) {
            jvmArgs("-Xdock:icon=$dockIconPath", "-Xdock:name=KaptionIt")
            systemProperty("apple.awt.application.name", "KaptionIt")
        }
    }
    tasks.named<JavaExec>("run") {
        configureComposeRunResources()
    }
    listOf(
        "hotRunJvm",
        "hotRunJvmAsync",
        "jvmRun",
        "hotDevJvm",
        "hotDevJvmAsync",
        "runRelease",
        "runDistributable",
        "runReleaseDistributable",
    ).forEach { tn ->
        tasks.findByName(tn)?.let { t ->
            if (t is JavaExec) {
                t.configureComposeRunResources()
            }
        }
    }
}
